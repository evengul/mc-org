package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.engine.model.ItemNode
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode

/**
 * Stage 1 of the planning engine: decide *how* each item is obtained.
 *
 * Walks the item-source graph from the targets and chooses exactly one source
 * per item (scorer-driven by default, [PlanOverrides] pins win), producing a
 * [SelectedDag]. Shared ingredients resolve to a single node regardless of how
 * many targets need them.
 *
 * Structural guarantees:
 * - The chosen DAG is acyclic. A candidate source is rejected outright when its
 *   ingredients (directly, or through their already-chosen chains) lead back to
 *   the item being resolved — "craft diamonds from a diamond block" can never
 *   be selected while mining is available, and a pure pack/unpack cycle ends in
 *   [PlanNodeStatus.BLOCKED] instead of a lying plan.
 * - Items in [supplied] terminate expansion wherever they appear, including as
 *   intermediates, and carry their [SupplySource] label.
 * - Tags are not silently expanded: without a [PlanOverrides.tagMember] choice
 *   they stay [PlanNodeStatus.OPEN_TAG]; with one, the member item takes the
 *   tag's place in the DAG (so its demand merges with other demand for it).
 *
 * Selection is amount-aware: the demand seen while expanding feeds the
 * recipe-threshold bonus. Demands computed here are provisional (first-encounter,
 * propagated through provisional craft counts) — quantify() computes the exact
 * totals on the final DAG.
 */
object PlanSelector {

    fun select(
        graph: ItemSourceGraph,
        targets: List<PlanTarget>,
        supplied: Map<String, SupplySource> = emptyMap(),
        overrides: PlanOverrides = PlanOverrides.NONE,
        context: PlanContext = PlanContext()
    ): SelectedDag = Selection(graph, supplied, overrides, context).run(targets)

    private class Selection(
        private val graph: ItemSourceGraph,
        private val supplied: Map<String, SupplySource>,
        private val overrides: PlanOverrides,
        private val context: PlanContext
    ) {
        private val scorer = SelectionScorer(graph, supplied, context)
        private val nodes = LinkedHashMap<String, SelectedNode>()

        /** itemId -> every itemId reachable through its chosen chain (incl. itself). */
        private val closures = HashMap<String, Set<String>>()

        /** Provisional demand per item, accumulated as consumers are expanded. */
        private val demands = HashMap<String, Long>()

        /** Items currently being expanded — the DFS ancestry used for cycle rejection. */
        private val expanding = LinkedHashSet<String>()

        private val itemsById: Map<String, List<ItemNode>> =
            graph.getAllItems().groupBy { it.itemId }

        fun run(targets: List<PlanTarget>): SelectedDag {
            val roots = LinkedHashMap<String, String>()
            for (target in targets) {
                if (target.amount <= 0) continue
                roots[target.item.id] = resolve(target.item, target.amount, depth = 0)
            }
            return SelectedDag(nodes, roots)
        }

        /**
         * Resolves one item to a node, creating it on first encounter, and returns
         * the id of the node that fulfils it (tag overrides may redirect to a member).
         */
        private fun resolve(item: MinecraftId, demand: Long, depth: Int): String {
            val resolved = redirectTag(graphItemFor(item))
            val id = resolved.id

            demands.merge(id, demand, Long::plus)
            if (id in nodes) return id

            val supply = supplied[id]
            if (supply != null) {
                terminal(resolved, PlanNodeStatus.SUPPLIED, supply)
                return id
            }
            if (resolved is MinecraftTag) {
                terminal(resolved, PlanNodeStatus.OPEN_TAG)
                return id
            }
            if (depth >= context.maxDepth) {
                terminal(resolved, PlanNodeStatus.BLOCKED)
                return id
            }

            val candidates = graph.getSourcesForItem(resolved)
            if (candidates.isEmpty()) {
                terminal(resolved, PlanNodeStatus.BLOCKED)
                return id
            }

            val pinnedKey = overrides.sourceByItem[id]
            val ordered: List<SourceNode> = if (pinnedKey != null) {
                listOfNotNull(candidates.firstOrNull { it.getKey() == pinnedKey })
            } else {
                rank(resolved, candidates)
            }

            // Two passes: first only candidates whose entire ingredient chain can
            // still be completed; then, if nothing qualifies, accept the best
            // candidate whose direct ingredients merely don't cycle — its chain
            // expands as far as possible and shows BLOCKED at the failure point,
            // which beats a bare BLOCKED root. Pins skip the strict pass: an
            // impossible pin should stay visible, not silently fall back.
            if (pinnedKey == null) {
                for (candidate in ordered) {
                    if (expand(resolved, candidate, depth, requireCompletable = true)) return id
                }
            }
            for (candidate in ordered) {
                if (expand(resolved, candidate, depth, requireCompletable = false)) return id
            }

            terminal(resolved, PlanNodeStatus.BLOCKED)
            return id
        }

        /** Tries to commit [candidate] for [item]; false when structurally infeasible. */
        private fun expand(item: MinecraftId, candidate: SourceNode, depth: Int, requireCompletable: Boolean): Boolean {
            val id = item.id
            val requirements = graph.getRequiredItems(candidate).sortedBy { it.itemId }
            if (!feasible(id, requirements, requireCompletable)) return false

            val itemNode = ItemNode(item)
            val producedQuantity = graph.getProducedQuantity(candidate, itemNode)
            val expectedYield = graph.getExpectedYield(candidate, itemNode)
            val crafts = attemptsFor(demands.getValue(id), producedQuantity, expectedYield)

            expanding.add(id)
            val requires = ArrayList<PlanRequirement>(requirements.size)
            val closure = HashSet<String>().apply { add(id) }
            for (requirement in requirements) {
                val quantity = graph.getRequiredQuantity(candidate, requirement)
                val childId = resolve(requirement.item, crafts * quantity, depth + 1)
                requires.add(PlanRequirement(childId, quantity))
                closure.addAll(closures[childId] ?: setOf(childId))
            }
            expanding.remove(id)

            closures[id] = closure
            nodes[id] = SelectedNode(
                item = item,
                status = if (requirements.isEmpty()) PlanNodeStatus.RAW_GATHER else PlanNodeStatus.RESOLVED,
                source = candidate,
                producedQuantity = producedQuantity,
                expectedYield = expectedYield,
                requires = requires
            )
            return true
        }

        /**
         * A candidate is infeasible when an ingredient would close a cycle — it is
         * the item itself, an item still being expanded above us, or an already
         * chosen chain containing either. With [requireCompletable], an ingredient
         * must additionally be acquirable at all while the current ancestry is
         * off-limits: this rejects "unpack the storage block you would first have
         * to craft from the item" chains and plain dead ends whenever a viable
         * alternative exists.
         */
        private fun feasible(id: String, requirements: List<ItemNode>, requireCompletable: Boolean): Boolean {
            val memo = HashMap<String, Boolean>()
            for (requirement in requirements) {
                val effective = redirectTag(requirement.item)
                val effectiveId = effective.id

                if (effectiveId == id || effectiveId in expanding) return false
                closures[effectiveId]?.let { closure ->
                    if (id in closure || closure.any { it in expanding }) return false
                }

                if (requireCompletable && !acquirable(effective, effectiveId, id, memo)) return false
            }
            return true
        }

        /**
         * Can [item] be obtained through *some* chain that never touches the item
         * being resolved or anything still being expanded above it? Walks the raw
         * graph (not just chosen chains); already-resolved nodes answer from their
         * status and closure.
         */
        private fun acquirable(item: MinecraftId, id: String, resolvingId: String, memo: MutableMap<String, Boolean>): Boolean {
            if (id == resolvingId || id in expanding) return false
            if (id in supplied) return true
            if (item is MinecraftTag) return true
            memo[id]?.let { return it }

            nodes[id]?.let { existing ->
                val usable = existing.status != PlanNodeStatus.BLOCKED &&
                    closures[id].orEmpty().none { it == resolvingId || it in expanding }
                memo[id] = usable
                return usable
            }

            // Provisional false cuts graph cycles below this item on the current walk.
            memo[id] = false
            val sources = graph.getSourcesForItem(item)
            val hasConstructiveSibling = sources.any { it.sourceType.isConstructive() }
            val usable = sources.any { source ->
                // Breaking the item's own placed block cannot ground a chain when a
                // constructive alternative exists (a recipe, or a game mechanic like
                // concrete-by-water) — the placed block had to be obtained first. Without
                // this, "unpack iron block <- break a placed iron block" would count
                // as a complete acquisition path.
                if (hasConstructiveSibling && SelectionScorer.isSelfBlockLoot(item, source)) return@any false
                graph.getRequiredItems(source).all { requirement ->
                    val effective = redirectTag(requirement.item)
                    acquirable(effective, effective.id, resolvingId, memo)
                }
            }
            memo[id] = usable
            return usable
        }

        private fun rank(item: MinecraftId, candidates: Set<SourceNode>): List<SourceNode> {
            val hasConstructiveSibling = candidates.any { it.sourceType.isConstructive() }
            val demand = demands.getValue(item.id)
            return candidates.sortedWith(
                compareByDescending<SourceNode> { scorer.score(item, it, demand, hasConstructiveSibling) }
                    .thenByDescending { it.sourceType.isRecipe() }
                    .thenBy { it.getKey() }
            )
        }

        /** Swaps a caller-provided id for the graph's own node of the same kind, when present. */
        private fun graphItemFor(item: MinecraftId): MinecraftId {
            val matches = itemsById[item.id] ?: return item
            val sameKind = matches.firstOrNull { (it.item is MinecraftTag) == (item is MinecraftTag) }
            return (sameKind ?: matches.first()).item
        }

        /** Applies a [PlanOverrides.tagMember] choice, validated against the tag's members. */
        private fun redirectTag(item: MinecraftId): MinecraftId {
            if (item !is MinecraftTag) return item
            val memberId = overrides.tagMember[item.id] ?: return item
            val member = item.content.firstOrNull { it.id == memberId } ?: return item
            return graphItemFor(member)
        }

        private fun terminal(item: MinecraftId, status: PlanNodeStatus, supply: SupplySource? = null) {
            nodes[item.id] = SelectedNode(item = item, status = status, supply = supply)
            closures[item.id] = setOf(item.id)
        }
    }
}
