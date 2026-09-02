package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceSource.SourceType
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode

/**
 * A sketch of the acquisition-cost model proposed as a replacement for [SelectionScorer]
 * (MCO-320's follow-up). **Nothing calls this in production.** It exists so the two models
 * can be run side by side against the real ingested graph and their disagreements read,
 * before anything is ripped out.
 *
 * ## What it is
 *
 * One number per item: **how long a unit of it takes to acquire**, in minutes, following the
 * cheapest chain. Not a score — a cost, in a unit you can argue about.
 *
 * ```
 * c(item)     = min over its sources s of cost(s, item)
 * cost(s, i)  = effort(s.type) / yield(s, i)  +  Σ_req (qty_req / qty_out(s, i)) · c(req)
 * c(tag)      = min over members
 * c(supplied) = 0
 * ```
 *
 * Computed by the same relaxation the depth walk should have used: every cost starts at
 * infinity and only ever decreases, so cycles never improve anything and need no
 * visiting-set guard, and the answer does not depend on who asked.
 *
 * ## Why this shape
 *
 * [SelectionScorer] adds eight constants in incommensurable units — a base score in points,
 * a count of ingredients, a hop count, a yield ratio, a demand step. None of them is weighted
 * by **quantity**, which is what acquisition cost actually turns on: one ingot makes nine
 * nuggets, and three ingots plus two sticks make one pickaxe. That is why each fix has had to
 * be fitted into the window left by the previous one, and why the windows keep narrowing.
 *
 * Here the factors are commensurable by construction, so several existing penalties stop
 * being rules and start being consequences:
 *
 * - **Pack/unpack cycles.** An ingot from a block costs `effort/9 + (1/9)·c(block)`, and the
 *   block costs `effort + 9·c(ingot)` — so the route is always dearer than the ingot itself
 *   and can never win. `RECIPROCAL_PENALTY` (and its hand-swept [11,15] window) is not needed.
 * - **Breaking what you placed.** [selfCost] makes a self-block-loot source cost the item plus
 *   the effort of breaking it, which is strictly worse than any other route to the item.
 *   `SELF_BLOCK_LOOT_PENALTY = 200` is not needed.
 * - **Low-yield drops.** Dividing by expected yield *is* the penalty: 0.33 sticks per witch
 *   kill costs three kills. `LOW_YIELD_PENALTY_WEIGHT` and its cap are not needed — and both
 *   turned out to be unpinned by any test, so nothing records what they were meant to do.
 * - **Depth and ingredient count.** Both are crude proxies for the summed cost of the chain,
 *   which this sums directly. Neither is needed, which matters because depth was measured to
 *   be a flat `d1` on 96 of 98 real candidates — a constant wearing a metric's clothes.
 *
 * ## What it does not decide
 *
 * Effort per source type is the one felt input left, and it stays felt — Mojang's data has
 * quantities and drop rates but no notion of how long anything takes a player. The gain is
 * that there is **one** table of interpretable numbers to argue about, in minutes, rather
 * than eight interacting constants in points; and that it can be swept against
 * `CuratedSelectionTest` as a calibration set rather than tuned until the suite goes green.
 */
class UnitCostModel(
    private val graph: ItemSourceGraph,
    private val supplied: Set<String> = emptySet(),
    private val effort: EffortTable = EffortTable.DEFAULT,
    /**
     * Relaxation passes. Convergence needs one pass per link in the longest chain that is
     * still improving; 64 is far above anything real (the deepest measured chain is 3) and
     * the loop exits early the moment a pass changes nothing.
     */
    private val maxPasses: Int = 64,
    /**
     * Diagnostics only: fixes the order [relax] sweeps items in. Null uses the graph's own
     * order, which is what production would do. Supplying a permutation is how the audit
     * checks whether any answer depends on that order.
     */
    private val itemOrder: List<app.mcorg.engine.model.ItemNode>? = null,
) {

    /** Minutes per unit for every item and tag in the graph. [UNREACHABLE] where nothing produces it. */
    val cost: Map<String, Double> by lazy { relax() }

    /** Diagnostics: how many sweeps [relax] needed. Equal to [maxPasses] means it never settled. */
    var passesUsed: Int = -1
        private set

    /** Diagnostics: true when a sweep changed nothing, i.e. the answer is a real fixpoint. */
    var converged: Boolean = false
        private set

    /** The cheapest source for [item], or null when nothing produces it at a finite cost. */
    fun best(item: MinecraftId): SourceNode? =
        graph.getSourcesForItem(item)
            .filter { costOf(it, item) < UNREACHABLE }
            .minByOrNull { costOf(it, item) }

    /** Minutes per unit of [item] obtained through [source], given the settled costs. */
    fun costOf(source: SourceNode, item: MinecraftId): Double = costOf(source, item, cost)

    private fun costOf(source: SourceNode, item: MinecraftId, c: Map<String, Double>): Double {
        val itemNode = graph.getItemNode(item) ?: return UNREACHABLE
        val out = yieldOf(source, item)
        if (out <= 0.0) return UNREACHABLE

        // Breaking the block you placed is not a source of the item — it is the item back,
        // minus the work of placing it. Costing it as "the item, plus the effort of breaking
        // it" makes it strictly worse than however you got the item, so it can never win.
        // The old model needed a 200-point penalty to say the same thing.
        //
        // But only when there is another way to make the item. Breaking an acacia log *is* how
        // you get an acacia log; a naturally-occurring block has no constructive sibling, and
        // treating its own loot as circular prices the honest raw gather at infinity. The
        // shipped scorer gates on exactly this (`hasConstructiveSibling`), and the first run of
        // this model without the gate proved why: acacia_log came out at 25.7 minutes and lost
        // to a chest. The gate is not scaffolding around a weak score — it is a fact about the
        // graph, and it survives the change of model.
        if (SelectionScorer.isSelfBlockLoot(item, source) && hasConstructiveSibling(item)) {
            val own = c[item.id] ?: UNREACHABLE
            return if (own >= UNREACHABLE) UNREACHABLE else own + effort.of(source.sourceType)
        }

        var total = effort.of(source.sourceType) / out
        for (requirement in graph.getRequiredItems(source)) {
            val each = c[requirement.itemId] ?: UNREACHABLE
            if (each >= UNREACHABLE) return UNREACHABLE
            val needed = graph.getRequiredQuantity(source, requirement).coerceAtLeast(1)
            total += (needed / out) * each
        }
        return if (total >= UNREACHABLE) UNREACHABLE else total
    }

    /**
     * Is there a deliberate way to *make* this item — a recipe, or an in-world transform?
     * Mirrors the shipped selector's own test, so the two models answer the circularity
     * question the same way and their disagreements are about cost rather than about that.
     */
    private fun hasConstructiveSibling(item: MinecraftId): Boolean =
        graph.getSourcesForItem(item).any { it.sourceType.isConstructive() }

    /**
     * Units of [item] produced per attempt at [source].
     *
     * Expected yield leads where the data has it, because that is the honest per-attempt
     * figure for a drop — a witch gives 0.33 sticks, so a stick is three witches. Recipes
     * carry a produced quantity instead (one log, four planks). Anything with neither is one
     * per attempt, which is the right default for a source that simply hands you the thing.
     */
    private fun yieldOf(source: SourceNode, item: MinecraftId): Double {
        val node = graph.getItemNode(item) ?: return 0.0
        graph.getExpectedYield(source, node)?.let { if (it > 0.0) return it }
        return graph.getProducedQuantity(source, node).coerceAtLeast(1).toDouble()
    }

    private fun relax(): Map<String, Double> {
        val c = HashMap<String, Double>()
        val items = itemOrder ?: graph.getAllItems().toList()
        for (node in items) c[node.item.id] = if (node.item.id in supplied) 0.0 else UNREACHABLE

        repeat(maxPasses) { pass ->
            passesUsed = pass + 1
            var changed = false
            for (node in items) {
                val item = node.item
                if (item.id in supplied) continue

                val relaxed = if (item is MinecraftTag) {
                    // A tag is the decision, not a step: it costs exactly what the member the
                    // user would pick costs, which is the cheapest one.
                    item.content.minOfOrNull { c[it.id] ?: UNREACHABLE } ?: UNREACHABLE
                } else {
                    graph.getSourcesForItem(item)
                        .minOfOrNull { costOf(it, item, c) } ?: UNREACHABLE
                }

                if (relaxed < (c[item.id] ?: UNREACHABLE)) {
                    c[item.id] = relaxed
                    changed = true
                }
            }
            if (!changed) {
                converged = true
                return c
            }
        }
        return c
    }

    companion object {
        /** Not "expensive" — genuinely no finite chain. Kept well below MAX so sums cannot overflow. */
        const val UNREACHABLE = 1e9
    }
}

/**
 * Minutes of player time per *attempt* at a source of this type — one craft, one furnace
 * slot, one mob killed, one chest found.
 *
 * These are the model's only felt numbers, and they are deliberately all in one place, in one
 * unit, so that disagreeing with a selection means disagreeing with a duration rather than
 * with the gap between two point values. They are first estimates: the point of the sketch is
 * to see which selections they move, not to be right yet.
 */
class EffortTable(
    private val minutes: Map<String, Double>,
    /** For a type not named below — treated as ordinary manual work rather than free. */
    val default: Double = 1.0,
) {
    fun of(type: SourceType): Double = minutes[type.id] ?: default

    fun with(type: SourceType, value: Double): EffortTable =
        EffortTable(minutes + (type.id to value), default)

    companion object {
        /**
         * Villager trading: curing or breeding a villager, getting the profession, restocking.
         * One number for all fifteen professions until there is evidence they differ — the old
         * model gave them one base score too (70, and 65 for the wandering trader), so this
         * keeps the same claim without pretending to more precision.
         */
        private val TRADE_MINUTES: Map<String, Double> = listOf(
            SourceType.TradeTypes.ARMORER, SourceType.TradeTypes.BUTCHER,
            SourceType.TradeTypes.CARTOGRAPHER, SourceType.TradeTypes.CLERIC,
            SourceType.TradeTypes.FARMER, SourceType.TradeTypes.FISHERMAN,
            SourceType.TradeTypes.FLETCHER, SourceType.TradeTypes.LEATHERWORKER,
            SourceType.TradeTypes.LIBRARIAN, SourceType.TradeTypes.MASON,
            SourceType.TradeTypes.SHEPHERD, SourceType.TradeTypes.SMITH,
            SourceType.TradeTypes.TOOLSMITH, SourceType.TradeTypes.WEAPONSMITH,
        ).associate { it.id to 3.0 } + (SourceType.TradeTypes.WANDERING_TRADER.id to 8.0)

        val DEFAULT = EffortTable(
            mapOf(
                // Bench work: the action is instant, the cost is walking to the station.
                SourceType.RecipeTypes.CRAFTING_SHAPED.id to 0.05,
                SourceType.RecipeTypes.CRAFTING_SHAPELESS.id to 0.05,
                SourceType.RecipeTypes.CRAFTING_TRANSMUTE.id to 0.05,
                SourceType.RecipeTypes.CRAFTING_IMBUE.id to 0.05,
                SourceType.RecipeTypes.STONECUTTING.id to 0.05,
                SourceType.RecipeTypes.SMITHING_TRANSFORM.id to 0.2,

                // Furnaces are real time, and it is per item: 10s smelting, 5s blasting.
                SourceType.RecipeTypes.SMELTING.id to 0.17,
                SourceType.RecipeTypes.BLASTING.id to 0.08,
                SourceType.RecipeTypes.SMOKING.id to 0.08,
                SourceType.RecipeTypes.CAMPFIRE_COOKING.id to 0.5,

                // Mining a block you have found. The finding is not modelled — see the note
                // on structure loot below for why that asymmetry is the interesting one.
                SourceType.LootTypes.BLOCK.id to 0.05,
                SourceType.LootTypes.BLOCK_INTERACT.id to 0.05,
                SourceType.MechanicTypes.COLLECT.id to 0.05,
                SourceType.MechanicTypes.IN_WORLD_TRANSFORM.id to 0.1,

                // Killing something: find it, fight it.
                SourceType.LootTypes.ENTITY.id to 0.5,
                SourceType.LootTypes.ENTITY_INTERACT.id to 0.3,
                SourceType.LootTypes.SHEARING.id to 0.2,

                // Structure loot is the expensive thing the old model priced at 60 points and
                // could not express: a chest is not slow to open, it is slow to *reach*. This
                // is where trades and chests should stop beating a recipe, and the number that
                // most wants arguing about.
                SourceType.LootTypes.CHEST.id to 15.0,
                SourceType.LootTypes.ARCHAEOLOGY.id to 20.0,
                SourceType.LootTypes.EQUIPMENT.id to 5.0,
                SourceType.LootTypes.GIFT.id to 30.0,
                SourceType.LootTypes.FISHING.id to 2.0,
                SourceType.LootTypes.BARTER.id to 1.0,
            ) + TRADE_MINUTES,
        )

    }
}
