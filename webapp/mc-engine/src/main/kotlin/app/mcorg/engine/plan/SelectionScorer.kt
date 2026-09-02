package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.PlacedForms
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode
import app.mcorg.domain.model.resources.ResourceSource

/**
 * Scores candidate sources for one item during selection. Higher is better.
 *
 * Factors (additive):
 * 1. Source-type base score ([ResourceSource.SourceType.score]).
 * 2. Efficiency bonus — output/input ratio above 1.0. Not applied to trades
 *    (output-per-emerald is an exchange rate, not saved effort) nor to reciprocal
 *    recipes (storage block -> item), whose input embeds N of the output.
 * 2b. Reciprocal penalty — a recipe that converts the item into a form of itself
 *    rather than acquiring it (ingot <- 9 nuggets, ingot <- iron block). The test is
 *    symmetric, so it also demotes the reverse (nuggets <- ingot), which is a real
 *    acquisition route; see [reciprocalPenalty] for why that trade is taken.
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
 * e.g. diamond <- diamond block <- 9 diamonds) is mostly settled before scoring:
 * the selector rejects such candidates structurally. Factor 2b covers what is left
 * — where the ingredient has some *other* viable source, so the cycle is real but
 * not structural and scoring is the only thing that can catch it.
 */
/**
 * The individual factors behind a candidate's [SelectionScorer.score], for
 * diagnostics. Bonuses are positive; penalties are stored positive and were
 * subtracted to reach [total].
 */
internal data class ScoreBreakdown(
    val base: Int,
    val efficiency: Int,
    val supplied: Int,
    val recipeThreshold: Int,
    val reciprocal: Int,
    val selfBlockLoot: Int,
    val lowYield: Int,
    val requirementCount: Int,
    val requirementPenalty: Int,
    val chainDepth: Int,
    val depthPenalty: Int,
    val total: Int,
    val requiredItemIds: List<String>
)

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
        hasConstructiveSibling: Boolean
    ): Int {
        var total = source.sourceType.score

        // Computed once: the craftedFrom walk behind it is unmemoized and both the
        // efficiency bonus and the reciprocal penalty ask the same question.
        val reciprocal = isReciprocalUnpack(item, source)

        total += efficiencyBonus(item, source, reciprocal)
        total += suppliedBonus(source)
        total += recipeThresholdBonus(item, source, demand)
        total -= reciprocalPenalty(reciprocal)
        total -= selfBlockLootPenalty(item, source, hasConstructiveSibling)
        total -= lowYieldPenalty(item, source)

        val requirements = graph.getRequiredItems(source)
        total -= requirements.size * REQUIREMENT_PENALTY
        total -= chainDepth(requirements.map { it.item }) * DEPTH_PENALTY

        return total
    }

    /**
     * Diagnostic-only: the same factors [score] sums, returned individually so a
     * dump can show *why* a candidate ranks where it does. Reuses the identical
     * helpers, so the [ScoreBreakdown.total] is byte-for-byte equal to [score];
     * this method is read-only and changes no ranking behaviour.
     */
    internal fun breakdown(
        item: MinecraftId,
        source: SourceNode,
        demand: Long,
        hasConstructiveSibling: Boolean
    ): ScoreBreakdown {
        val requirements = graph.getRequiredItems(source)
        val base = source.sourceType.score
        val isReciprocal = isReciprocalUnpack(item, source)
        val efficiency = efficiencyBonus(item, source, isReciprocal)
        val supplied = suppliedBonus(source)
        val recipeThreshold = recipeThresholdBonus(item, source, demand)
        val reciprocal = reciprocalPenalty(isReciprocal)
        val selfBlockLoot = selfBlockLootPenalty(item, source, hasConstructiveSibling)
        val lowYield = lowYieldPenalty(item, source)
        val requirementPenalty = requirements.size * REQUIREMENT_PENALTY
        val chainDepth = chainDepth(requirements.map { it.item })
        val depthPenalty = chainDepth * DEPTH_PENALTY
        val total = base + efficiency + supplied + recipeThreshold -
            reciprocal - selfBlockLoot - lowYield - requirementPenalty - depthPenalty
        return ScoreBreakdown(
            base = base,
            efficiency = efficiency,
            supplied = supplied,
            recipeThreshold = recipeThreshold,
            reciprocal = reciprocal,
            selfBlockLoot = selfBlockLoot,
            lowYield = lowYield,
            requirementCount = requirements.size,
            requirementPenalty = requirementPenalty,
            chainDepth = chainDepth,
            depthPenalty = depthPenalty,
            total = total,
            requiredItemIds = requirements.map { it.itemId }.sorted()
        )
    }

    private fun lowYieldPenalty(item: MinecraftId, source: SourceNode): Int {
        val itemNode = graph.getItemNode(item) ?: return 0
        val expectedYield = graph.getExpectedYield(source, itemNode) ?: return 0
        if (expectedYield >= 1.0) return 0
        return ((1.0 / expectedYield - 1.0) * LOW_YIELD_PENALTY_WEIGHT).toInt()
            .coerceAtMost(LOW_YIELD_PENALTY_CAP)
    }

    private fun efficiencyBonus(item: MinecraftId, source: SourceNode, isReciprocal: Boolean): Int {
        // Trades are not rewarded for output multiplicity: a trade's output per
        // input is an emerald exchange rate, not a measure of saved effort, and a
        // high one (8 sand or 4 gunpowder per emerald) would otherwise vault a
        // wandering-trader source above mining the block or killing the mob.
        if (source.sourceType.isTrade()) return 0
        // Unpacking a storage block is not efficient: its lone ingredient embeds
        // nine of the output and must itself be obtained. See [isReciprocalUnpack].
        if (isReciprocal) return 0
        val itemNode = graph.getItemNode(item) ?: return 0
        val totalInput = graph.getRequiredQuantities(source).values.sum()
        if (totalInput == 0) return 0
        val ratio = graph.getProducedQuantity(source, itemNode).toDouble() / totalInput
        return ((ratio - 1.0) * EFFICIENCY_WEIGHT).toInt().coerceAtLeast(0)
    }

    /**
     * A "reciprocal unpack" recipe turns a denser, packed form back into the item
     * it is made from — iron_ingot from iron_block, where iron_block is in turn
     * crafted from iron_ingot. Its 9x output triggers a large [efficiencyBonus],
     * but the lone ingredient is not cheaper than the output: it embeds nine of
     * them and must itself be obtained. Rewarding that fake efficiency made "loot
     * a storage block from a chest and unpack it" outscore mining or smelting the
     * item directly.
     *
     * Detected as a recipe whose single ingredient is, directly or transitively,
     * crafted from this very item — the containment is transitive so a layered
     * form is caught too (copper_ingot from waxed_copper_block <- copper_block <-
     * copper_ingot). The pack direction (block from 9 items) already scores zero
     * efficiency (ratio < 1), so only the unpack is affected *for the efficiency bonus*
     * — but [reciprocalPenalty] reuses this same predicate and is symmetric, so the pack
     * direction does take that penalty. Genuine high-yield
     * crafts (log -> 4 planks) are not reciprocal and keep their bonus.
     */
    private fun isReciprocalUnpack(item: MinecraftId, source: SourceNode): Boolean {
        if (!source.sourceType.isRecipe()) return false
        val ingredient = graph.getRequiredItems(source).singleOrNull() ?: return false
        if (ingredient.itemId == item.id) return false
        return craftedFrom(ingredient.item, item.id, HashSet())
    }

    /**
     * True when [candidate] is produced — directly or through a chain of recipes —
     * by consuming [target]. Follows recipe edges only, bounded by [PlanContext.maxDepth]
     * and guarded against cycles, so it identifies a packed form of [target]
     * without walking the whole graph.
     */
    private fun craftedFrom(candidate: MinecraftId, target: String, visiting: MutableSet<String>): Boolean {
        if (candidate.id == target) return true
        if (visiting.size >= context.maxDepth || !visiting.add(candidate.id)) return false
        val result = graph.getSourcesForItem(candidate).any { source ->
            source.sourceType.isRecipe() &&
                graph.getRequiredItems(source).any { craftedFrom(it.item, target, visiting) }
        }
        visiting.remove(candidate.id)
        return result
    }

    /**
     * Penalizes a recipe that makes the item out of a form of *itself* — an iron
     * ingot from 9 iron nuggets, or from an iron block. Both ingredients are
     * themselves made from ingots, so neither recipe acquires iron; it only
     * changes iron's shape.
     *
     * Zeroing the efficiency bonus was not enough: these recipes never earned one
     * (9 nuggets -> 1 ingot has a ratio below 1), so they kept the full crafting base
     * and beat smelting on base score alone — routing a build's whole iron requirement
     * through nuggets smelted from armour (MCO-317).
     *
     * Scope: the selector rejects *structurally* circular candidates before scoring.
     * This catches the rest — where the ingredient has some other viable source, so the
     * cycle is real but not structural.
     *
     * **The test is symmetric**, so crafting nuggets *from* an ingot is demoted too,
     * even though that is how a player really gets nuggets — at small demand it loses to
     * smelting iron equipment. Restricting the penalty to the concentrating direction
     * (ratio < 1) was measured against the real graph and is worse: then
     * `iron_ingot_from_iron_block` escapes and wins outright. The real repair is costing
     * open tags, which currently score as free (MCO-320); this penalty is the bounded part.
     *
     * Gold keeps its nugget route where it matters: with a piglin farm declared,
     * [suppliedBonus] (+30) outweighs this penalty. Pinned by
     * `gold_ingot - a declared nugget farm beats mining...` and its sibling.
     *
     * **Over-match, measured across the whole graph and accepted.** Because the predicate
     * is symmetric and keys on a *single* ingredient, it also touches cases with no
     * reciprocal meaning. A sweep of all produced items found 54 carrying a penalised
     * candidate, and **no decision changes anywhere** — but three groups lose margin:
     * - the **pack** direction of all 18 storage blocks (craft a block of iron now leads
     *   looting one from a bastion chest by 5 instead of 20);
     * - 12 **armour** pieces, via `craft(helmet) <- ingot <- 9 nuggets <- smelt(helmet)`.
     *   It catches armour and not tools only because armour recipes have one distinct
     *   ingredient and tools have two — an artifact of `singleOrNull`, not a judgement;
     * - `stone <-> cobblestone` and `deepslate <-> cobbled_deepslate`, which are genuine
     *   2-cycles, so smelting cobblestone into stone is penalised. Block loot at 100
     *   still wins, so nothing flips.
     *
     * All three are latent rather than active. If a future weight change narrows those
     * gaps, they are the first places to break.
     */
    private fun reciprocalPenalty(isReciprocal: Boolean): Int =
        if (isReciprocal) RECIPROCAL_PENALTY else 0

    private fun suppliedBonus(source: SourceNode): Int {
        if (supplied.isEmpty()) return 0
        return graph.getRequiredItems(source).count { it.itemId in supplied } * SUPPLIED_BONUS
    }

    private fun recipeThresholdBonus(item: MinecraftId, source: SourceNode, demand: Long): Int {
        if (demand < context.recipeThreshold) return 0
        if (!source.sourceType.isRecipe()) return 0
        // Withhold the bulk bonus when the item is simply mineable: a recipe that
        // only converts one raw block into another (stone -> cobblestone via
        // stonecutting) saves no effort over mining more, so it must not leapfrog
        // the raw gather at bulk demand. Self-block loot (break what you placed)
        // is not a real gather and does not count.
        if (hasMineableSource(item)) return 0
        return RECIPE_THRESHOLD_BONUS
    }

    /** True when the item drops from breaking a *different* block (a real raw gather). */
    private fun hasMineableSource(item: MinecraftId): Boolean =
        graph.getSourcesForItem(item).any {
            it.sourceType == ResourceSource.SourceType.LootTypes.BLOCK && !isSelfBlockLoot(item, it)
        }

    private fun selfBlockLootPenalty(item: MinecraftId, source: SourceNode, hasConstructiveSibling: Boolean): Int {
        if (!hasConstructiveSibling) return 0
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
        /**
         * The suite admits **[11, 15]**, verified by sweeping the value: 10 fails three
         * tests, 11 through 15 pass, 16 fails the guard test. 13 is the midpoint. The
         * two bounds bite at *different* demands, which is why a plausible-looking value
         * can pass the bulk case and still break the other:
         * - **at least 11**: at bulk both candidates take [RECIPE_THRESHOLD_BONUS], so
         *   ingot-from-nuggets is 130 against smelting's 120; the penalty must clear
         *   that base gap.
         * - **at most 15**: at small demand neither does, and unpacking an ingot into
         *   nuggets must stay ahead of looting them — a nugget has no "mine a nugget"
         *   path. In the curated fixture that candidate scores 75 (its ingredient sits
         *   at chain depth 2), so at 15 it lands exactly on chest loot's 60 and survives
         *   only on the recipe-first tie-break, with zero score margin. 13 keeps a real
         *   margin, which is why it is preferred over the top of the window.
         *
         * Both bounds are pinned in `CuratedSelectionTest`. Changing this without
         * re-running it will silently break one of them.
         */
        private const val RECIPROCAL_PENALTY = 13
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
            if (stem == item.id.substringAfterLast(':')) return true
            // A block is not always named after the item you placed to make it: you put down
            // `minecraft:redstone` and the world holds `minecraft:redstone_wire`. Comparing
            // names caught `blocks/beacon.json` and missed that one, so the planner offered
            // "break placed redstone dust" as a way to obtain redstone dust. Only the
            // reversible forms count — a crop yields more than was planted, and farmland needs
            // a hoe standing between the dirt and the block, so neither is re-collection.
            return PlacedForms.isReversibleFormOf("minecraft:$stem", item.id)
        }
    }
}
