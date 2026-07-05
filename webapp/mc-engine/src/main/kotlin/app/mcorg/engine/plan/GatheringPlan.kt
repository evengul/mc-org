package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.SourceNode

/**
 * Lifecycle status of a node in a gathering plan. Every node carries one, so a
 * finished raw-gather leaf is always distinguishable from "awaiting a variant
 * choice" or "dead end".
 */
enum class PlanNodeStatus {
    /** A source is chosen and its ingredient chain is expanded. */
    RESOLVED,

    /** Deliberate terminal — mine / kill / gather from the world. Source chosen, no inputs. */
    RAW_GATHER,

    /** Satisfied externally (farm or linked project); chain not expanded. Carries a [SupplySource]. */
    SUPPLIED,

    /** A tag whose concrete variant the user has not picked yet (e.g. "any planks"). Quantity is known. */
    OPEN_TAG,

    /** No feasible source: dead end, unavoidable cycle, or an impossible user pin. Needs attention. */
    BLOCKED
}

/**
 * An ingredient edge: one execution of the parent's source consumes
 * [quantityPerCraft] of the item identified by [itemId].
 */
data class PlanRequirement(
    val itemId: String,
    val quantityPerCraft: Int
)

/**
 * A quantified node of the plan DAG. One node per item — demand from every target
 * that needs the item accumulates here.
 *
 * @param quantity total amount of the item required across all targets.
 * @param crafts how many times the chosen action is executed (`ceil(quantity / producedQuantity)`).
 * @param leftover overproduction from ceil-rounding (`crafts * producedQuantity - quantity`).
 *   This is the per-node leftover bank: V1 reports it but never reuses it; the V2
 *   surplus optimizer will draw from it.
 * @param source the chosen source ([PlanNodeStatus.RESOLVED] / [PlanNodeStatus.RAW_GATHER]).
 * @param supply the external supply label ([PlanNodeStatus.SUPPLIED]).
 * @param producedQuantity output of one execution of [source] (1 for terminals).
 * @param expectedYield average output per attempt from loot-table data; when
 *   set, [crafts] counts attempts (`ceil(quantity / expectedYield)`) and the
 *   probabilistic surplus is not banked as [leftover].
 * @param requires ingredient edges into sibling nodes (empty for terminals).
 */
data class PlanNode(
    val item: MinecraftId,
    val quantity: Long,
    val crafts: Long,
    val leftover: Long,
    val status: PlanNodeStatus,
    val source: SourceNode? = null,
    val supply: SupplySource? = null,
    val producedQuantity: Int = 1,
    val expectedYield: Double? = null,
    val requires: List<PlanRequirement> = emptyList()
)

/**
 * The kind of play activity a node maps to. Declaration order is the preferred
 * session order: resolve open questions first, collect what farms already
 * produce, then head out gathering/hunting/looting/trading, and finish at the
 * furnaces and crafting tables.
 */
enum class ActivityGroup {
    /** OPEN_TAG / BLOCKED — needs a user decision before the plan is real. */
    NEEDS_ATTENTION,

    /** Pick up from a farm or a linked project. */
    COLLECT_SUPPLIED,

    /** Break/mine blocks in the world. */
    GATHER,

    /** Kill or interact with entities. */
    HUNT,

    /** Chests, fishing, archaeology, gifts, equipment drops. */
    LOOT,

    /** Villager trades and piglin bartering. */
    TRADE,

    /** Furnace work — smelting, blasting, smoking, campfire. */
    SMELT,

    /** Bench work — crafting, stonecutting, smithing. */
    CRAFT,

    /** Unrecognized source types. */
    OTHER
}

/**
 * One row of the consolidated activity list — a [PlanNode] flattened for display,
 * ordered so that every ingredient appears before the activity consuming it.
 */
data class Activity(
    val item: MinecraftId,
    val quantity: Long,
    val crafts: Long,
    val leftover: Long,
    val status: PlanNodeStatus,
    val group: ActivityGroup,
    val source: SourceNode? = null,
    val supply: SupplySource? = null
)

/**
 * Per-target drill-down: the selected chain of a single target expanded into a
 * tree, with quantities as if this target were gathered **alone** (per-node ceil
 * without cross-target accumulation). Shared nodes therefore may show a larger
 * combined total in [GatheringPlan.activityList] sums than here per-target —
 * the consolidated DAG rounds once per node, which is the whole point of
 * accumulate-then-ceil.
 */
data class TargetTree(
    val item: MinecraftId,
    val quantityIfAlone: Long,
    val craftsIfAlone: Long,
    val status: PlanNodeStatus,
    val source: SourceNode? = null,
    val supply: SupplySource? = null,
    val children: List<TargetTree> = emptyList()
)

/**
 * The quantified gathering plan: a DAG of [PlanNode]s plus two derived views.
 *
 * Internally one node per item (shared ingredients accumulate); the views are
 * computed from the DAG, never stored:
 * - [activityList] — project-wide roadmap, topologically ordered.
 * - [perTarget] — drill-down tree for one target's chain.
 *
 * @param nodes itemId -> quantified node.
 * @param targets the requested targets (amounts net of collected).
 * @param roots target itemId -> fulfilling node id (differs only for tag targets
 *   redirected to a member item via [PlanOverrides.tagMember]).
 */
class GatheringPlan(
    val nodes: Map<String, PlanNode>,
    val targets: List<PlanTarget>,
    val roots: Map<String, String> = targets.associate { it.item.id to it.item.id }
) {

    /** A plan is complete exactly when nothing awaits a user choice or attention. */
    val complete: Boolean by lazy {
        nodes.values.none { it.status == PlanNodeStatus.OPEN_TAG || it.status == PlanNodeStatus.BLOCKED }
    }

    /**
     * The consolidated roadmap: every node once, ingredients strictly before the
     * activities that consume them. Ordering within that constraint is stable
     * and deterministic (see [ActivityOrdering]).
     */
    val activityList: List<Activity> by lazy { ActivityOrdering.order(nodes) }

    /** The activities awaiting a user decision (open tags and blocked nodes). */
    val needsAttention: List<Activity> by lazy {
        activityList.filter { it.group == ActivityGroup.NEEDS_ATTENTION }
    }

    /**
     * Reverse-provenance: node id -> the set of target item ids whose selected chain
     * transitively consumes that node — "what does gathering this go toward". Powers
     * the "feeds 24 Birch Door · 40 Chest" annotation.
     *
     * - A node's own target id is not in its own set — a target does not feed itself.
     * - A target that is also an ingredient of another target is attributed to that
     *   other target (and its own id propagates to its ingredients).
     * - A pure target nothing else consumes maps to an empty set (no annotation).
     *
     * Computed by seeding each target root with its target id and propagating down the
     * [PlanNode.requires] edges in reverse-topological order (consumers before
     * ingredients), so a single pass fills every node. O(nodes + edges).
     */
    val feeders: Map<String, Set<String>> by lazy {
        val result = HashMap<String, MutableSet<String>>(nodes.size)
        nodes.keys.forEach { result[it] = HashSet() }

        // nodeId -> target item ids it directly fulfills (differs from the node id only
        // for a tag target redirected to a concrete member via [roots]).
        val ownTargets = HashMap<String, MutableSet<String>>()
        for ((targetItemId, nodeId) in roots) {
            ownTargets.getOrPut(nodeId) { HashSet() }.add(targetItemId)
        }

        // activityList is topological (ingredients before consumers); reversed gives
        // consumers first, so a node's feeder set is complete before we push it down.
        for (activity in activityList.asReversed()) {
            val id = activity.item.id
            val node = nodes[id] ?: continue
            val attribution = HashSet(result.getValue(id))
            ownTargets[id]?.let { attribution.addAll(it) }
            if (attribution.isEmpty()) continue
            for (req in node.requires) {
                if (req.itemId != id) result[req.itemId]?.addAll(attribution)
            }
        }
        result
    }

    /**
     * Drill-down tree for one target, or null if [targetItemId] is not a target.
     * Quantities are "if gathered alone" — see [TargetTree].
     */
    fun perTarget(targetItemId: String): TargetTree? {
        val target = targets.find { it.item.id == targetItemId } ?: return null
        val rootId = roots[targetItemId] ?: targetItemId
        val root = nodes[rootId] ?: return null
        return expand(root, target.amount, path = HashSet())
    }

    private fun expand(node: PlanNode, demand: Long, path: MutableSet<String>): TargetTree {
        val crafts = attemptsFor(demand, node.producedQuantity, node.expectedYield)
        // The selection stage guarantees an acyclic DAG; the path guard only protects
        // against hand-constructed cyclic input.
        val children = if (!path.add(node.item.id)) emptyList() else try {
            node.requires.mapNotNull { req ->
                nodes[req.itemId]?.let { expand(it, crafts * req.quantityPerCraft, path) }
            }
        } finally {
            path.remove(node.item.id)
        }
        return TargetTree(
            item = node.item,
            quantityIfAlone = demand,
            craftsIfAlone = crafts,
            status = node.status,
            source = node.source,
            supply = node.supply,
            children = children
        )
    }
}

internal fun ceilDiv(amount: Long, per: Int): Long {
    require(per >= 1) { "producedQuantity must be >= 1, was $per" }
    return (amount + per - 1) / per
}

/**
 * Executions needed to cover [demand]: attempts against the average loot yield
 * when one is known, otherwise crafts against the nominal output quantity.
 */
internal fun attemptsFor(demand: Long, producedQuantity: Int, expectedYield: Double?): Long {
    if (expectedYield != null && expectedYield > 0) {
        return kotlin.math.ceil(demand / expectedYield).toLong()
    }
    return ceilDiv(demand, producedQuantity)
}

/**
 * Ordering rule for the consolidated activity list.
 *
 * Base constraint (non-negotiable): topological — every node's ingredients appear
 * before it. Within that constraint, ready activities are emitted grouped by
 * [ActivityGroup] in play-session order, so all mining clusters together before
 * the furnace work and so on; the final tiebreak is the item id, making the list
 * stable across data refreshes and JVM runs.
 */
internal object ActivityOrdering {

    fun order(nodes: Map<String, PlanNode>): List<Activity> {
        // Kahn's algorithm over ingredient edges (requires -> node), leaves first.
        // pending[id] = number of this node's ingredients not yet emitted.
        val pending = HashMap<String, Int>(nodes.size)
        val consumers = HashMap<String, MutableList<String>>()
        for ((id, node) in nodes) {
            val inGraph = node.requires.count { it.itemId in nodes && it.itemId != id }
            pending[id] = inGraph
            for (req in node.requires) {
                if (req.itemId in nodes && req.itemId != id) {
                    consumers.getOrPut(req.itemId) { mutableListOf() }.add(id)
                }
            }
        }

        val byGroupThenId = compareBy<String>({ nodes.getValue(it).group().ordinal }, { it })
        val ready = sortedSetOf(byGroupThenId)
        pending.forEach { (id, count) -> if (count == 0) ready.add(id) }

        val result = ArrayList<Activity>(nodes.size)
        val emitted = HashSet<String>(nodes.size)
        while (ready.isNotEmpty()) {
            val id = ready.first().also { ready.remove(it) }
            emitted.add(id)
            result.add(nodes.getValue(id).toActivity())
            for (consumer in consumers[id].orEmpty()) {
                val remaining = pending.getValue(consumer) - 1
                pending[consumer] = remaining
                if (remaining == 0) ready.add(consumer)
            }
        }

        // Defensive: a hand-constructed cyclic DAG would leave nodes unemitted.
        // Append them deterministically rather than dropping or looping.
        if (emitted.size < nodes.size) {
            nodes.keys.filter { it !in emitted }.sorted().forEach { id ->
                result.add(nodes.getValue(id).toActivity())
            }
        }
        return result
    }

    private fun PlanNode.group(): ActivityGroup = when (status) {
        PlanNodeStatus.OPEN_TAG, PlanNodeStatus.BLOCKED -> ActivityGroup.NEEDS_ATTENTION
        PlanNodeStatus.SUPPLIED -> ActivityGroup.COLLECT_SUPPLIED
        PlanNodeStatus.RESOLVED, PlanNodeStatus.RAW_GATHER -> source?.sourceType.toGroup()
    }

    private fun ResourceSource.SourceType?.toGroup(): ActivityGroup {
        if (this == null) return ActivityGroup.OTHER
        return when {
            this == ResourceSource.SourceType.LootTypes.BLOCK ||
                this == ResourceSource.SourceType.LootTypes.BLOCK_INTERACT ||
                this == ResourceSource.SourceType.MechanicTypes.COLLECT -> ActivityGroup.GATHER

            this == ResourceSource.SourceType.LootTypes.ENTITY ||
                this == ResourceSource.SourceType.LootTypes.ENTITY_INTERACT ||
                this == ResourceSource.SourceType.LootTypes.SHEARING -> ActivityGroup.HUNT

            this == ResourceSource.SourceType.LootTypes.BARTER || isTrade() -> ActivityGroup.TRADE

            isLoot() -> ActivityGroup.LOOT

            this == ResourceSource.SourceType.RecipeTypes.SMELTING ||
                this == ResourceSource.SourceType.RecipeTypes.BLASTING ||
                this == ResourceSource.SourceType.RecipeTypes.SMOKING ||
                this == ResourceSource.SourceType.RecipeTypes.CAMPFIRE_COOKING -> ActivityGroup.SMELT

            isConstructive() -> ActivityGroup.CRAFT

            else -> ActivityGroup.OTHER
        }
    }

    private fun PlanNode.toActivity() = Activity(
        item = item,
        quantity = quantity,
        crafts = crafts,
        leftover = leftover,
        status = status,
        group = group(),
        source = source,
        supply = supply
    )
}
