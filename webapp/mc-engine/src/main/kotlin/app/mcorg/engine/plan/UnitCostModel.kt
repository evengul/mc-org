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
 *   kill costs three kills. `LOW_YIELD_PENALTY_WEIGHT` is not needed — and it turned out to be
 *   unpinned by any test, so nothing records what it was meant to do. Its ceiling is already
 *   gone: it saturated three items' candidates to a dead tie and the pick fell through to the
 *   alphabetical source key, which is how a music disc came to be sourced from an ancient city.
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
 *
 * ## What the calibration sweep found (1.21.4, world 3, 992 multi-source items)
 *
 * Every entry in [EffortTable.DEFAULT] was swept across its plausible range with
 * `cost-diagnostics sweep`. Three results are about the *model*, not its constants, and are
 * worth more than the table:
 *
 * - **Most of the table decides nothing.** Nine of the twenty-one entries — smithing,
 *   campfire, block-interact, in-world transform, entity-interact, archaeology, equipment, and
 *   both trade rows — move not one selection anywhere in their range. They are not calibrated
 *   numbers; they are placeholders, and should be read as such.
 * - **Effort per source *type* is the wrong grain for the cases that matter.** One number
 *   covers all 995 block sources, so mining an emerald costs what mining dirt costs, and on
 *   26.x that mispricing is the only reason villager trades need a brake at all. `GIFT` lumps
 *   a chicken laying an egg in with winning a raid. Trade level (`cleric/5/...`) is in the
 *   source filename and would separate a novice trade from a master one. The next real gain
 *   in this model is per-source effort, not a better per-type number.
 * - **Two things it prices are artefacts of the ingested data, and no constant fixes them.**
 *   The fishing loot sub-tables (`gameplay/fishing/treasure.json`, `junk.json`) are ingested
 *   as standalone sources alongside their parent, so a name tag reads as 1-in-6 per cast
 *   rather than 1-in-6 of a 5% roll; and nothing in the graph *grows a crop*, so wheat's only
 *   priced route is a village chest, at any chest value.
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

    /** Diagnostics: the table this model was built with, so a caller can vary its grain. */
    val effortTable: EffortTable get() = effort

    /** Minutes per unit for every item and tag in the graph. [UNREACHABLE] where nothing produces it. */
    val cost: Map<String, Double> by lazy { relax() }

    private var passes: Int = -1
    private var settled: Boolean = false

    /**
     * Diagnostics: how many sweeps [relax] needed. Equal to [maxPasses] means it never settled.
     *
     * Reading this realises [cost] first. Without that the flag answers for a relaxation that has
     * not happened — a caller who asks before touching any cost is told `-1` and `false`, which
     * reads as "did not converge" rather than "did not run". That is exactly how it was misread
     * the first time.
     */
    val passesUsed: Int get() { cost; return passes }

    /** Diagnostics: true when a sweep changed nothing, i.e. the answer is a real fixpoint. */
    val converged: Boolean get() { cost; return settled }

    /**
     * The cheapest source for [item], or null when nothing produces it at a finite cost.
     *
     * **Equal costs are broken the way [PlanSelector] breaks them** — recipe first, then by
     * source key — rather than by whichever source the graph's iteration order happened to
     * hand over first. This is not a preference smuggled in as a tie-break: when two routes
     * cost the same the model has, by construction, no opinion, and an arbitrary answer there
     * is noise that reads as disagreement. Measured on 1.21.4 it is a third of the gap — 53 of
     * the 158 disagreements with the shipped scorer were exact-cost ties, most of them
     * `chiseled_*` blocks where crafting from two slabs and cutting one brick are the same
     * stone and the same click.
     */
    fun best(item: MinecraftId): SourceNode? {
        val candidates = feasibleSources(item)
            .map { it to costOf(it, item) }
            .filter { it.second < UNREACHABLE }
        val cheapest = candidates.minOfOrNull { it.second } ?: return null
        return candidates
            .filter { it.second <= cheapest + TIE_EPSILON * kotlin.math.max(1.0, cheapest) }
            .map { it.first }
            .sortedWith(
                compareByDescending<SourceNode> { it.sourceType.isRecipe() }
                    .thenBy { it.getKey() }
            )
            .first()
    }

    /**
     * The sources for [item] that could actually ground a chain — the structural half of the
     * decision, taken before any arithmetic.
     *
     * ## Why a cost model still needs this
     *
     * The model's own documentation used to claim that relaxing from +∞ meant "cycles never
     * improve anything and need no visiting-set guard". That is true only when a source
     * consumes at least as many of the item as it produces. It does not hold for a recipe with
     * **loop gain below 1**, and Minecraft ships nineteen of them: an armour-trim duplication
     * recipe takes one template and gives two, so
     *
     * ```
     * c = (small ingredient cost) + c/2   ->   c = 2 * (small ingredient cost)
     * ```
     *
     * converges perfectly well, to a number that beat the only real source (an ancient-city
     * chest) by a factor of 2700. The model was recommending "to obtain an ancient-city trim
     * template, take an ancient-city trim template and seven diamonds". Relaxation converges on
     * such a cycle; it does not reject it, because a fixpoint is a fixpoint whether or not it
     * describes anything a player can do.
     *
     * That is why this is a guard and not a penalty. No arithmetic makes a self-referential
     * derivation *expensive enough* — it has to be unavailable. [PlanSelector.feasible] and
     * [PlanSelector.acquirable] already ask this question for the shipped planner; this asks the
     * same one, so the two models agree on what is possible and disagree only about what is
     * cheap.
     *
     * ## The two conditions
     *
     * 1. **A source requiring the item it produces is rejected outright.** This is the
     *    duplication case, and it also removes the slow geometric series that made the
     *    relaxation need 67 passes when it was budgeted 64 and silently did not converge.
     * 2. **A requirement that cannot be obtained while avoiding the item is rejected.** Reaching
     *    the item somewhere below is not disqualifying on its own — `iron_nugget` from an ingot
     *    is legitimate even though `iron_ingot` also has a nine-nugget recipe, because the ingot
     *    has another route (smelting raw iron). The question is existential over chains, not
     *    universal: is there *some* way to get this ingredient that does not come back here.
     *
     * Computed once per item and cached: it is a property of the graph, not of the costs, so it
     * cannot change as the relaxation settles.
     */
    private fun feasibleSources(item: MinecraftId): Set<SourceNode> =
        feasibilityMemo.getOrPut(item.id) {
            graph.getSourcesForItem(item).filterTo(LinkedHashSet()) { source ->
                val requirements = graph.getRequiredItems(source)
                requirements.none { it.itemId == item.id } &&
                    requirements.all { acquirableAvoiding(it.item, item.id, HashMap()) }
            }
        }

    private val feasibilityMemo = HashMap<String, Set<SourceNode>>()

    /**
     * Is there any chain that produces [candidate] without passing through [avoidId]?
     *
     * Mirrors [PlanSelector.acquirable]. A tag answers true — it stands for a decision not yet
     * made, and the selector treats it the same way, so rejecting a candidate for depending on
     * an unanswered question would make the two models disagree about possibility rather than
     * cost. The provisional `false` written before recursing is what cuts cycles below this
     * item on the current walk.
     */
    private fun acquirableAvoiding(
        candidate: MinecraftId,
        avoidId: String,
        memo: MutableMap<String, Boolean>,
    ): Boolean {
        if (candidate.id == avoidId) return false
        if (candidate.id in supplied) return true
        if (candidate is MinecraftTag) return true
        memo[candidate.id]?.let { return it }

        memo[candidate.id] = false
        val usable = graph.getSourcesForItem(candidate).any { source ->
            graph.getRequiredItems(source).all { acquirableAvoiding(it.item, avoidId, memo) }
        }
        memo[candidate.id] = usable
        return usable
    }

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
            return if (own >= UNREACHABLE) UNREACHABLE else own + effort.of(source)
        }

        var total = effort.of(source) / out
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
            passes = pass + 1
            var changed = false
            for (node in items) {
                val item = node.item
                if (item.id in supplied) continue

                val relaxed = if (item is MinecraftTag) {
                    // A tag is the decision, not a step: it costs exactly what the member the
                    // user would pick costs, which is the cheapest one.
                    item.content.minOfOrNull { c[it.id] ?: UNREACHABLE } ?: UNREACHABLE
                } else {
                    feasibleSources(item)
                        .minOfOrNull { costOf(it, item, c) } ?: UNREACHABLE
                }

                if (relaxed < (c[item.id] ?: UNREACHABLE)) {
                    c[item.id] = relaxed
                    changed = true
                }
            }
            if (!changed) {
                settled = true
                return c
            }
        }
        return c
    }

    companion object {
        /** Not "expensive" — genuinely no finite chain. Kept well below MAX so sums cannot overflow. */
        const val UNREACHABLE = 1e9

        /**
         * How close two costs must be to count as the same. Floating-point summation over a
         * chain does not give bit-identical results for two genuinely equal routes, so an
         * exact `==` would leave the tie-break unreachable for the cases that need it. Kept
         * relative and tiny — a 1e-9 window cannot swallow a real difference, since the
         * smallest one in the table (0.05/6 against 0.05/4) is seven orders of magnitude
         * larger.
         */
        private const val TIE_EPSILON = 1e-9
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
    /**
     * False prices every source of a type alike, as this table did before MCO-494. Kept so the
     * two grains can be diffed against each other in one run: a net change in agreement hides
     * how many items actually moved, and in both directions.
     */
    private val perSource: Boolean = true,
) {
    /**
     * Minutes per attempt at this particular source.
     *
     * **The type sets the price of the action; the source sets the price of reaching it**
     * (MCO-494). Both halves are real and only the first was modelled: one number covered 995
     * block-loot sources on 1.21.4, so mining dirt, emerald ore and ancient debris all cost
     * 0.05 minutes. The model then reasoned impeccably from a premise that is plainly false.
     *
     * The symptom in the calibration sweep was that the load-bearing values had **no plateau** —
     * every one of block, crafting, chest and entity moved 5–12 selections for ±0.01, with no
     * stable region anywhere in their range. A parameter with no plateau is one being asked to
     * stand for something it cannot represent: there is no correct value for "the cost of mining
     * a block", because that is not one quantity.
     *
     * Splitting it does not make the table bigger in the way that matters. [FINDING] is short
     * enough to read in one screen and every line is a claim about the game a player can call
     * wrong, which is the property the whole cost model exists to have. What it must not become
     * is a constant per item — that is the eight-constant scorer again, wearing minutes.
     */
    fun of(source: SourceNode): Double =
        of(source.sourceType) * (if (perSource) findFactor(source) else 1.0)

    /** The action alone, with nothing said about reaching it. Kept for sweeps and tests. */
    fun of(type: SourceType): Double = minutes[type.id] ?: default

    fun with(type: SourceType, value: Double): EffortTable =
        EffortTable(minutes + (type.id to value), default, perSource)

    /** The same numbers at the old grain — one price per source type. */
    fun typeOnly(): EffortTable = EffortTable(minutes, default, perSource = false)

    companion object {

        /**
         * How much dearer this particular source is than the bare action, because of what it
         * takes to *reach* it. 1.0 means the type's number already tells the truth.
         *
         * Three things are keyed here, and nothing else is:
         *
         * 1. **Ore and debris.** Mining is quick; finding is not. `blocks/dirt.json` and
         *    `blocks/ancient_debris.json` are the same swing of the same pickaxe and nothing in
         *    Mojang's data separates them — hardness and tool tier are not ingested, and the
         *    thing that actually matters here is *availability*, which is not in the data at
         *    all. So this half is curated rather than derived, deliberately, and it is the part
         *    to distrust first. Every entry is a claim about how long a vein takes to find.
         * 2. **Chest tier.** A village chest and an ancient-city chest are not the same errand.
         *    The base chest number is an ordinary structure; this scales the two ends.
         * 3. **Trade level**, read from the source filename (`cleric/5/…`). A master trade costs
         *    a great deal of trading to unlock and a novice trade costs nothing, and the level
         *    is the one distinguishing fact the data *does* carry — no curation needed, which
         *    is why it is the most trustworthy line here.
         *
         * Everything else is 1.0. That is the point: the list is short enough to argue with.
         */
        internal fun findFactor(source: SourceNode): Double {
            val stem = source.filename.substringAfterLast('/').substringBeforeLast('.')
            return when {
                source.sourceType.isTrade() -> tradeLevelFactor(source.filename)
                source.sourceType == SourceType.LootTypes.CHEST ->
                    CHEST_FINDING.firstOrNull { source.filename.contains(it.first) }?.second ?: 1.0
                source.sourceType == SourceType.LootTypes.BLOCK -> BLOCK_FINDING[stem] ?: 1.0
                source.sourceType == SourceType.LootTypes.ENTITY -> ENTITY_FINDING[stem] ?: 1.0
                source.sourceType == SourceType.LootTypes.GIFT -> GIFT_FINDING[stem] ?: 1.0
                else -> 1.0
            }
        }

        /**
         * Minutes of searching per block mined, expressed as a multiple of the 3-second swing.
         * Numbers are deliberately round: they are estimates of an unmodelled quantity, and
         * false precision would only invite tuning them until the suite went green.
         */
        private val BLOCK_FINDING: Map<String, Double> = buildMap {
            // Strip-mining the nether roof for a handful of debris a session.
            put("ancient_debris", 160.0)          // ~8 min a block
            // Rare, biome-locked, single-block veins.
            for (ore in listOf("emerald_ore", "deepslate_emerald_ore")) put(ore, 60.0)   // ~3 min
            for (ore in listOf("diamond_ore", "deepslate_diamond_ore")) put(ore, 40.0)   // ~2 min
            // Common enough to meet while caving, but you are still looking for them.
            for (ore in listOf(
                "gold_ore", "deepslate_gold_ore", "nether_gold_ore",
                "lapis_ore", "deepslate_lapis_ore",
            )) put(ore, 8.0)                                                             // ~24 s
            for (ore in listOf(
                "iron_ore", "deepslate_iron_ore", "coal_ore", "deepslate_coal_ore",
                "copper_ore", "deepslate_copper_ore", "redstone_ore", "deepslate_redstone_ore",
            )) put(ore, 6.0)                                                             // ~18 s
            // Not scarce, but a diamond pickaxe and a slow break, at a lava pool you made.
            put("obsidian", 4.0)                                                         // ~12 s
        }

        /**
         * The same correction on the other side of the graph, and it is not optional.
         *
         * Pricing block *finding* while leaving every mob at a flat half-minute made the model
         * say "kill vindicators" for emeralds, because emerald ore had just become honest about
         * being rare while a raid mob was still priced like a cow standing in a field. Fixing
         * one side of an asymmetry turns the other side's falsehood into the deciding one — so
         * the two halves have to land together or not at all.
         *
         * Passive animals are the baseline: they are what the type's number was written for.
         */
        private val ENTITY_FINDING: Map<String, Double> = buildMap {
            // Common hostiles: they come to you, but you fight them.
            for (m in listOf("zombie", "skeleton", "creeper", "spider", "husk", "drowned")) put(m, 1.5)
            put("enderman", 3.0)
            // Nether structures — a journey, then a fortress or a bastion.
            for (m in listOf("blaze", "wither_skeleton", "ghast", "piglin", "hoglin", "magma_cube")) put(m, 10.0)
            // Raids, mansions, ocean monuments: an event or a structure, not an encounter.
            for (m in listOf(
                "vindicator", "evoker", "pillager", "ravager", "witch", "illusioner",
                "guardian", "elder_guardian",
            )) put(m, 20.0)
            put("shulker", 30.0)
            put("ender_dragon", 200.0)
        }

        /**
         * A chest is priced by the journey, and the journey is the **structure** — so this keys
         * on the path, not the filename.
         *
         * Keying on the exact stem was the first attempt and it was wrong in an instructive way:
         * pricing `reward_ominous` pushed five items onto `reward_ominous_unique`, a sibling
         * chest in the same room that had no entry. That is whack-a-mole, and a table that grows
         * one line per loot file is the per-item tuning this whole model exists to avoid. A
         * structure is one errand however many chests are in it.
         *
         * Ordered: the first path fragment that matches wins, so narrower entries lead.
         */
        private val CHEST_FINDING: List<Pair<String, Double>> = listOf(
            "ancient_city" to 3.0,
            "woodland_mansion" to 2.5,
            "end_city" to 2.5,
            "trial_chambers" to 2.5,
            "bastion" to 2.0,
            "stronghold" to 2.0,
            "buried_treasure" to 2.0,
            "shipwreck" to 1.5,
            // A village is the one structure you are probably already standing in.
            "village" to 0.5,
        )

        /**
         * `GIFT` is the clearest case in the table for this split: it covers a villager's
         * post-raid present *and* a chicken laying an egg. Calibrated to ten minutes, which is
         * about right for the raid and absurd for the chicken — and the chicken is the one a
         * plan actually depends on. Priced back down to what standing near a chicken costs.
         */
        private val GIFT_FINDING: Map<String, Double> = mapOf(
            "chicken_lay" to 0.02,
        )

        /**
         * `…/cleric/5/emerald_to_bottle_o_enchanting.json` -> level 5. Unlocking a master
         * villager is most of what a master trade costs, and it is the only part of this the
         * data states outright rather than us guessing.
         */
        private fun tradeLevelFactor(filename: String): Double {
            val level = LEVEL_IN_PATH.find(filename)?.groupValues?.get(1)?.toIntOrNull() ?: return 1.0
            return when (level) {
                1 -> 1.0
                2 -> 1.5
                3 -> 2.0
                4 -> 3.0
                else -> 4.0
            }
        }

        private val LEVEL_IN_PATH = Regex("""/([1-5])/""")

        /**
         * Villager trading: curing or breeding a villager, getting the profession, restocking.
         * Three minutes per transaction, amortising the villager you had to set up.
         *
         * **One number per profession is defensible; one number per *level* is not.** Sweeping
         * all fourteen professions together over [0.25, 30] against 26.2.0 moves 98 selections
         * at the bottom and 11 at the top, so the constant is far from inert — but nothing in
         * the sweep distinguishes an armorer from a shepherd, and nothing about the game does
         * either. What does differ is trade *level*: `armorer/3/...`, `cleric/5/...` — the tier
         * is right there in the source filename, and a master-level trade costs a great deal
         * of trading to unlock while a novice trade costs nothing. Level-5 trades win items
         * today (experience bottles, tipped arrows) at the same price as level-1 ones. Effort
         * keyed by [SourceType] cannot express that; it is the clearest case in the table for
         * per-source effort rather than per-type.
         *
         * **And it decides nothing on the data anyone is planning against.** No version before
         * 26.1.0 has a single trade source ingested — 1.21.4, the version world 3 runs, has
         * zero of 2571 — so on every real project today this number and the shipped scorer's
         * untested trade rules are both dead code. Admissible [2, 5] on 26.2.0; the whole
         * range is inert on 1.21.4.
         *
         * Note what the brake actually is on 26.x: a trade's emerald input is priced through
         * `c(emerald)`, and emerald ore is a block like any other, so an emerald costs 0.05
         * minutes. This constant is the only thing standing between the planner and "buy
         * everything". It is compensating for a price the model gets wrong, which is exactly
         * the kind of load the eight-constant scorer was full of.
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

        /**
         * The first-guess table, written in one sitting before anything was measured. Kept
         * so the calibration can be read as a diff and re-measured against what it replaced
         * (`cost-diagnostics table=sketch`), not so anything should use it.
         */
        val SKETCH = EffortTable(
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

        /**
         * The calibrated table. Every value below was swept across its plausible range against
         * the real 1.21.4 graph (992 items with more than one source, world 3) with
         * `cost-diagnostics sweep`, and carries the range over which the graph's selections do
         * not change — the "admissible region". Where a value is inert (no selection anywhere
         * turns on it at any plausible value) it says so, because an inert number must not be
         * mistaken for a calibrated one.
         *
         * Three values moved from [SKETCH]: chest 15 -> 10, gift 30 -> 10, fishing 2 -> 1.
         * Everything else survived its own sweep unchanged.
         *
         * The rationale on each line is the point. A number a player can call wrong is worth
         * more than a number nobody can argue with.
         */
        val DEFAULT = EffortTable(
            mapOf(
                // --- Bench work: one operation at a station you walked to. ---
                // 3 seconds: open the table, place the pattern, take the output. LOAD-BEARING
                // through its *ratio* to stonecutting and to block loot, not on its own.
                // Admissible [0.01, 0.10]; above that agreement falls away fast (0.20 ->
                // 83.6%, 0.50 -> 80.6%) as stone variants go to the stonecutter wholesale, and
                // at 2.0 the `bowl` fix is lost. Agreement is *higher* at 0.01-0.02 (91.4%),
                // but lowering it is the same lever as raising stonecutting, and is declined
                // for the same reason — see the next entry.
                SourceType.RecipeTypes.CRAFTING_SHAPED.id to 0.05,
                SourceType.RecipeTypes.CRAFTING_SHAPELESS.id to 0.05,
                SourceType.RecipeTypes.CRAFTING_TRANSMUTE.id to 0.05,
                SourceType.RecipeTypes.CRAFTING_IMBUE.id to 0.05,
                // Deliberately equal to crafting: both are one shift-click at a block you
                // placed. THE most load-bearing number in the table — the largest single group
                // of disagreements with the shipped scorer (45 items) is decided here, and
                // agreement climbs to 92.3% at 0.20. It is left equal anyway: raising it buys
                // that agreement by telling a *resource planner* to craft stairs 6-in-4-out
                // instead of cutting them 1-in-1-out, which wastes a third of the stone. The
                // shipped scorer prefers crafting because a recipe's base is 95 and
                // stonecutting's is 90; agreeing with it here means agreeing with that bias,
                // not with the game. There is no plateau: every step over [0.01, 1.0] moves
                // 5-70 selections, so nothing here is safe to nudge unmeasured.
                SourceType.RecipeTypes.STONECUTTING.id to 0.05,
                // Inert: swept [0.05, 3.0], not one selection changes. Netherite upgrades are
                // the only smithing recipes and nothing else produces those items.
                SourceType.RecipeTypes.SMITHING_TRANSFORM.id to 0.2,

                // --- Furnaces: the only three numbers here taken from the game rather than
                // felt. Smelting is 10s per item, blasting and smoking exactly half that, and
                // campfire cooking 30s. LOAD-BEARING only as an *ordering*: blasting must not
                // cost more than smelting or `copper_ingot` stops blasting (holds at 0.17,
                // lost at 0.30; also lost if smelting drops to 0.05). Their absolute size is
                // inert — smelting moves at most 3 selections anywhere over [0.02, 2.0].
                SourceType.RecipeTypes.SMELTING.id to 0.17,
                SourceType.RecipeTypes.BLASTING.id to 0.08,
                SourceType.RecipeTypes.SMOKING.id to 0.08,
                SourceType.RecipeTypes.CAMPFIRE_COOKING.id to 0.5,

                // --- Mining and collecting: a block you have already found. ---
                // 3 seconds: swing, pick up, step to the next one. LOAD-BEARING, and with no
                // plateau: every step over [0.02, 2.0] moves 8-64 selections. The fixes hold
                // over [0.02, 1.0] — 0.01 loses the wool fix, 2.0 loses copper — and from 0.5
                // upward raw materials start arriving by mob drop and bartering instead of
                // being mined, which is the wrong way round.
                // Note this one number covers all 995 block sources, so dirt and ancient
                // debris cost the same to mine — see the class docs on what that misses.
                SourceType.LootTypes.BLOCK.id to 0.05,
                // Inert: swept [0.01, 2.0]. One source in the whole graph (honey bottles).
                SourceType.LootTypes.BLOCK_INTERACT.id to 0.05,
                // Inert: swept [0.01, 2.0], one item moves at 0.10 (water, bucket vs ice).
                SourceType.MechanicTypes.COLLECT.id to 0.05,
                // Inert: swept [0.02, 2.0], nothing moves.
                SourceType.MechanicTypes.IN_WORLD_TRANSFORM.id to 0.1,

                // --- Mobs: find it, fight it, pick the drops up. ---
                // 30 seconds a kill. Admissible [0.25, 2.0]: at 0.10 killing sheep undercuts
                // shearing them and the 16-colour wool fix is lost, and from 5 upward mob
                // drops lose to structure loot, which is the wrong way round for anything
                // farmable. The flat middle is one point wide — [0.25, 1.0] moves one item.
                SourceType.LootTypes.ENTITY.id to 0.5,
                // Inert: swept [0.05, 5.0], nothing moves (no such sources in 1.21.4).
                SourceType.LootTypes.ENTITY_INTERACT.id to 0.3,
                // 12 seconds: walk up to the sheep, shear, collect. LOAD-BEARING at the top —
                // at 0.6 white wool goes back to crafting from string and the 16-colour fix
                // starts unravelling. Admissible [0.02, 0.40].
                SourceType.LootTypes.SHEARING.id to 0.2,

                // --- Structure loot: the number that decides how far a chest reaches. ---
                // Ten minutes to *reach* a chest: find the structure, travel, clear it. Sweep
                // (0.25 -> 120) says the flat region is [8, 10] and the region within one
                // selection of it is [5, 20]: below 5 the model starts
                // looting things you would make or mine (tnt, lodestone, arrows, paper, iron,
                // even coal and emerald at 0.5); above 25 it starts pushing genuinely
                // chest-shaped items onto worse routes (potions to fishing, splash potions to
                // bartering). It can never remove chests: 18 items — horse armour, banner
                // patterns, music discs, trial keys, enchanted golden apples — have no other
                // route at any price, so "never chosen" is not on the dial.
                SourceType.LootTypes.CHEST.id to 10.0,
                // Inert: swept [1, 90], nothing moves — the 0.04-0.25 expected yields already
                // multiply any value into last place. 20 minutes is one brushing expedition.
                SourceType.LootTypes.ARCHAEOLOGY.id to 20.0,
                // Inert: swept [0.5, 20]. Trial-chamber mob equipment; a fight, not a chest.
                SourceType.LootTypes.EQUIPMENT.id to 5.0,
                // Was 30. At 30 an egg costs more than looting one from a chest, which no
                // player would do — a chicken lays one for free. The type mixes a raid you
                // fight (hero-of-the-village gifts) with an animal you keep (chicken_lay,
                // armadillo_shed), and one number cannot be right for both; 10 prices the raid
                // roughly and the chicken far too dear. Admissible [5, 19]: below 5 a second
                // group of items starts arriving as gifts, above ~19 the egg goes back to a
                // chest. The real repair is per-source effort, not a better single value.
                SourceType.LootTypes.GIFT.id to 10.0,
                // Was 2. One minute of attention per catch — cast, wait 5-30s, reel, recast.
                // Nearly inert (flat over [1, 2], one item either side out to 20), and the
                // items it does decide are
                // distorted by an extraction bug rather than by this number: the fishing
                // sub-tables (treasure.json, junk.json) are ingested as standalone sources, so
                // a name tag reads as 1-in-6 per cast instead of 1-in-6 of the 5% treasure
                // roll. No value of this constant fixes that.
                SourceType.LootTypes.FISHING.id to 1.0,
                // One minute per barter: build the drop spot, toss the ingot, wait. The gold
                // you throw is not modelled as an input (loot sources consume nothing), so
                // this number carries it. Stable over [1, 10]; below 1 a few nether blocks
                // start arriving by barter.
                SourceType.LootTypes.BARTER.id to 1.0,
            ) + TRADE_MINUTES,
        )
    }
}
