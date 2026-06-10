package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode
import app.mcorg.domain.model.resources.ResourceSource

/**
 * Scores candidate sources for one item during selection. Higher is better.
 *
 * Factors (additive):
 * 1. Source-type base score ([ResourceSource.SourceType.score]).
 * 2. Efficiency bonus — output/input ratio above 1.0.
 * 3. Supplied bonus — ingredients satisfied by the supplied map are nearly free.
 * 4. Recipe-threshold bonus — at bulk demand, recipes beat repeated gathering.
 * 5. Self-block-loot penalty — breaking a block that *is* the item (beacon,
 *    redstone lamp) is "break what you crafted", not a natural acquisition;
 *    mining a different block that drops the item (diamond ore) is not penalized.
 * 6. Low-yield penalty — a loot source averaging under one item per attempt
 *    (0.33 sticks per witch kill) costs effort proportional to the attempts
 *    needed, so cheap recipes win even at small demand. Sources averaging at
 *    least one per attempt (cow leather, golem iron) are untouched.
 * 7. Requirement-count and chain-depth penalties.
 *
 * Circular crafting (a recipe whose ingredient chain contains the item itself,
 * e.g. diamond <- diamond block <- 9 diamonds) is not a scoring concern: the
 * selector rejects such candidates structurally before they are scored.
 */
internal class SelectionScorer(
    private val graph: ItemSourceGraph,
    private val supplied: Map<String, SupplySource>,
    private val context: PlanContext
) {

    private val minDepthMemo = HashMap<String, Int>()

    /**
     * Set when a depth walk hits an item already on the walk's own stack. A
     * result computed under that condition is only valid for this path — caching
     * it would give other walks a depth that pretends the in-progress item is
     * unreachable — so tainted results are never memoized.
     */
    private var depthWalkTainted = false

    fun score(
        item: MinecraftId,
        source: SourceNode,
        demand: Long,
        hasRecipeSibling: Boolean
    ): Int {
        var total = source.sourceType.score

        total += efficiencyBonus(item, source)
        total += suppliedBonus(source)
        total += recipeThresholdBonus(source, demand)
        total -= selfBlockLootPenalty(item, source, hasRecipeSibling)
        total -= lowYieldPenalty(item, source)

        val requirements = graph.getRequiredItems(source)
        total -= requirements.size * REQUIREMENT_PENALTY
        total -= chainDepth(requirements.map { it.item }) * DEPTH_PENALTY

        return total
    }

    private fun lowYieldPenalty(item: MinecraftId, source: SourceNode): Int {
        val itemNode = graph.getItemNode(item) ?: return 0
        val expectedYield = graph.getExpectedYield(source, itemNode) ?: return 0
        if (expectedYield >= 1.0) return 0
        return ((1.0 / expectedYield - 1.0) * LOW_YIELD_PENALTY_WEIGHT).toInt()
            .coerceAtMost(LOW_YIELD_PENALTY_CAP)
    }

    private fun efficiencyBonus(item: MinecraftId, source: SourceNode): Int {
        val itemNode = graph.getItemNode(item) ?: return 0
        val totalInput = graph.getRequiredQuantities(source).values.sum()
        if (totalInput == 0) return 0
        val ratio = graph.getProducedQuantity(source, itemNode).toDouble() / totalInput
        return ((ratio - 1.0) * EFFICIENCY_WEIGHT).toInt().coerceAtLeast(0)
    }

    private fun suppliedBonus(source: SourceNode): Int {
        if (supplied.isEmpty()) return 0
        return graph.getRequiredItems(source).count { it.itemId in supplied } * SUPPLIED_BONUS
    }

    private fun recipeThresholdBonus(source: SourceNode, demand: Long): Int {
        if (demand < context.recipeThreshold) return 0
        return if (source.sourceType.isRecipe()) RECIPE_THRESHOLD_BONUS else 0
    }

    private fun selfBlockLootPenalty(item: MinecraftId, source: SourceNode, hasRecipeSibling: Boolean): Int {
        if (!hasRecipeSibling) return 0
        return if (isSelfBlockLoot(item, source)) SELF_BLOCK_LOOT_PENALTY else 0
    }

    /**
     * Depth of the shallowest acquisition chain below the given ingredients.
     * Supplied items and terminals count as depth 0; a deeper requirement tree
     * penalizes the candidate. Cycles encountered during the walk count as
     * unreachable through that source and fall back to alternatives.
     */
    private fun chainDepth(ingredients: Collection<MinecraftId>): Int {
        if (ingredients.isEmpty()) return 0
        return ingredients.maxOf { minDepth(it, HashSet()) }
    }

    private fun minDepth(item: MinecraftId, visiting: MutableSet<String>): Int {
        if (item.id in supplied) return 0
        if (item is MinecraftTag) return 0
        minDepthMemo[item.id]?.let { return it }
        if (!visiting.add(item.id)) {
            depthWalkTainted = true
            return UNREACHABLE_DEPTH
        }

        val taintedBefore = depthWalkTainted
        depthWalkTainted = false

        val sources = graph.getSourcesForItem(item)
        val depth = if (sources.isEmpty()) 0 else {
            val viable = sources.minOf { source ->
                val requirements = graph.getRequiredItems(source)
                if (requirements.isEmpty()) 1
                else {
                    val below = requirements.maxOf { minDepth(it.item, visiting) }
                    if (below >= UNREACHABLE_DEPTH) UNREACHABLE_DEPTH else 1 + below
                }
            }
            viable.coerceAtMost(context.maxDepth)
        }

        visiting.remove(item.id)
        if (!depthWalkTainted) minDepthMemo[item.id] = depth
        depthWalkTainted = depthWalkTainted || taintedBefore
        return depth
    }

    companion object {
        private const val EFFICIENCY_WEIGHT = 20
        private const val SUPPLIED_BONUS = 30
        private const val RECIPE_THRESHOLD_BONUS = 50
        private const val SELF_BLOCK_LOOT_PENALTY = 200
        private const val LOW_YIELD_PENALTY_WEIGHT = 20
        private const val LOW_YIELD_PENALTY_CAP = 60
        private const val DEPTH_PENALTY = 5
        private const val REQUIREMENT_PENALTY = 10
        private const val UNREACHABLE_DEPTH = Int.MAX_VALUE / 2

        /**
         * Breaking a block that *is* the item ("blocks/beacon.json" for
         * minecraft:beacon) only re-collects something already placed — never a
         * natural acquisition. Mining a different block that drops the item
         * (diamond ore for diamonds) does not match.
         */
        internal fun isSelfBlockLoot(item: MinecraftId, source: SourceNode): Boolean {
            if (source.sourceType != ResourceSource.SourceType.LootTypes.BLOCK) return false
            val stem = source.filename.substringAfterLast('/').substringBeforeLast('.')
            return stem == item.id.substringAfterLast(':')
        }
    }
}
