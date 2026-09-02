package app.mcorg.pipeline.resources

import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.pipeline.project.resources.GetResourceProductionStep

/**
 * An idea in the bank that produces something this plan demands (MCO-294).
 *
 * [produces] is what the design makes and the plan wants; [alsoRemoves] is the work that
 * disappears underneath it — see [FarmSuggestions] for why the two are separate.
 */
data class FarmSuggestion(
    val ideaId: Int,
    val ideaName: String,
    val produces: List<CoveredDemand>,
    val alsoRemoves: List<CoveredDemand>,
) {
    /** Total demand this design takes off the plan — the ranking key, and the honest headline. */
    val unitsRemoved: Long = produces.sumOf { it.quantity } + alsoRemoves.sumOf { it.quantity }

    /** Every item this suggestion accounts for, for marking the roll-up. */
    val itemIds: Set<String> = (produces + alsoRemoves).mapTo(mutableSetOf()) { it.itemId }

    /**
     * How long this design runs before the plan's demand for it is covered — the longest of its
     * measured lines, since one farm makes all of its outputs at once.
     *
     * Null when no produced line has a rate, and **under-stated when only some do**: a design
     * with a measured bone rate and an unmeasured blaze rod rate reports the bones' time. That
     * is the same posture as everything else here — claim the part that was measured, never
     * invent the part that was not — and it is why [FarmSuggestionChoices] ranks a measured
     * design above an unmeasured one rather than treating "unknown" as "instant".
     */
    val coverageHours: Double? = produces.mapNotNull { it.hoursToCover }.maxOrNull()
}

/**
 * One demand row a suggestion accounts for.
 *
 * [ratePerHour] is null for a row in [FarmSuggestion.alsoRemoves] (the design does not make
 * this, it makes what this feeds) and for a produced item whose rate the author never
 * measured — `idea_production_rates.rate_per_hour` is nullable exactly for that (V2_57_0).
 */
data class CoveredDemand(
    val itemId: String,
    val itemName: String,
    val quantity: Long,
    val ratePerHour: Int? = null,
) {
    /** Hours of running to cover this demand, or null when the rate is unknown or zero. */
    val hoursToCover: Double? =
        ratePerHour?.takeIf { it > 0 }?.let { quantity.toDouble() / it }
}

/** An idea that produces at least one item, as read from the bank. */
data class IdeaProducer(
    val ideaId: Int,
    val ideaName: String,
    /** itemId -> best rate across the idea's modes, null when never measured. */
    val rates: Map<String, Int?>,
)

/**
 * Matches plan demand against the idea bank (MCO-294).
 *
 * ## Why this does not read the farm-scale roll-up
 *
 * [FarmScaleDemands] classifies **RAW_GATHER leaves only**, which is right for "what quantity
 * is worth a farm" and wrong for "which design covers it". Measured on the dogfood world:
 * the roll-up's 4th-largest line is *Deepslate Iron Ore, 33,049* — because the plan mines ore
 * — while the bank's iron farm produces `minecraft:iron_ingot`, and the plan carries
 * *Iron Ingot, 33,049, RESOLVED/SMELT* one edge above it. Keyed on the roll-up, the single
 * most valuable suggestion in the world matches nothing at all.
 *
 * So matching reads every node whose demand a design could answer, not just the leaves.
 * [SUPPLIED][PlanNodeStatus.SUPPLIED] is excluded — an operational farm already covers it, and
 * suggesting a second one is the failure MCO-401 was careful to avoid — and
 * [OPEN_TAG][PlanNodeStatus.OPEN_TAG] is excluded because a tag is not an item and has no id to
 * match on. [BLOCKED][PlanNodeStatus.BLOCKED] stays in: a farm for something the graph has no
 * source for is the *most* useful suggestion this can make.
 *
 * ## What the world already covers is not demand (MCO-458, MCO-461)
 *
 * [PlanNodeStatus.SUPPLIED] catches only farms that are already `DONE` (MCO-287), which leaves two
 * doors onto the same absurdity. A farm *under construction* — or one recorded through MCO-298
 * with no source design at all — has its own build materials, so its plan would offer a design
 * producing exactly what that farm produces. And a farm merely *planned* is matched too, so one
 * page could carry MCO-299's notice ("63,213 Redstone Dust will come from Witch Hut Farm once it
 * is running") beside a suggestion to import that same farm again.
 *
 * `alreadyCovered` closes both: an item some project in this world is already going to produce is
 * not demand a design can answer, whatever state that project is in. The rule is deliberately
 * blunt — a *bigger* design for a covered item is excluded too. A 924,000/h cobblestone farm is a
 * real answer to demand a 231,000/h one cannot meet, but "your cobble farm needs 4,000 cobble,
 * build this other cobble farm" is the wrong first read, and the upgrade case needs a rate
 * comparison and UI copy that no real data has asked for yet. Even's call, 2026-08-25.
 *
 * ## Why the unit is the idea, not the item
 *
 * A farm produces several things. The bank's stick producer **is** the Witch Hut Farm, which is
 * already the answer for 63,273 Redstone Dust; per-item lines would list that one design three
 * times over — for redstone, for sticks, and for glass bottles — and invite someone to build a
 * witch hut "for sticks". One line per design, naming everything it covers, ranked by the work
 * it removes.
 *
 * A design **qualifies** only if something it directly produces is itself farm-scale. Without
 * that, an idea making 216 glass bottles would be suggested against a threshold it never met.
 *
 * ## Coverage is conservative on purpose
 *
 * Building the iron farm does not only cover the ingots; the 33,049 ore below them stops being
 * work too. That knock-on is real and worth showing, but only where it is certain: a node counts
 * as removed when **every** consumer of it is removed. Oak logs feed sticks *and* planks *and*
 * chests, so a design covering only the sticks leaves the log demand alone — apportioning it
 * would need per-edge attribution this deliberately does not attempt. Under-claiming is the
 * safe direction: the suggestion is an argument for building something, and an inflated number
 * is an argument that falls apart the first time someone checks it.
 */
object FarmSuggestions {

    /**
     * Designs worth building for this plan, most work removed first.
     *
     * [producers] is the bank, already narrowed to what the viewer may see — this function is
     * pure and does no filtering of its own beyond the plan.
     *
     * [alreadyCovered] is every item this world is already going to produce; see the section
     * above for why those are not demand at all rather than demand ranked lower.
     */
    fun of(
        plan: GatheringPlan,
        threshold: Int,
        producers: List<IdeaProducer>,
        alreadyCovered: Set<String> = emptySet(),
    ): List<FarmSuggestion> {
        if (producers.isEmpty()) return emptyList()

        val demand = matchableDemand(plan, alreadyCovered)
        if (demand.isEmpty()) return emptyList()

        return producers
            .mapNotNull { producer -> producer.suggestionFor(plan, demand, threshold) }
            .sortedWith(compareByDescending<FarmSuggestion> { it.unitsRemoved }.thenBy { it.ideaName })
    }

    /** itemId -> the demand a design could answer. */
    private fun matchableDemand(plan: GatheringPlan, alreadyCovered: Set<String>): Map<String, DemandRow> =
        plan.activityList
            .filter { it.status != PlanNodeStatus.SUPPLIED && it.status != PlanNodeStatus.OPEN_TAG }
            .filter { it.item.id !in alreadyCovered }
            .associate { it.item.id to DemandRow(it.item.id, it.item.name, it.quantity) }

    private data class DemandRow(val itemId: String, val itemName: String, val quantity: Long)

    private fun IdeaProducer.suggestionFor(
        plan: GatheringPlan,
        demand: Map<String, DemandRow>,
        threshold: Int,
    ): FarmSuggestion? {
        val direct = rates.keys.filter { it in demand }.toSet()
        if (direct.none { demand.getValue(it).quantity >= threshold }) return null

        val removed = coveredBy(plan, direct)

        return FarmSuggestion(
            ideaId = ideaId,
            ideaName = ideaName,
            produces = direct
                .map { demand.getValue(it).toCovered(rates[it]) }
                .sortedByDescending { it.quantity },
            alsoRemoves = removed
                .filter { it !in direct }
                .mapNotNull { demand[it]?.toCovered(null) }
                .sortedByDescending { it.quantity },
        )
    }

    private fun DemandRow.toCovered(rate: Int?) = CoveredDemand(itemId, itemName, quantity, rate)

    /**
     * [direct] plus every node whose consumers are all covered.
     *
     * One reverse-topological pass. `activityList` puts ingredients before the activities that
     * consume them, so walking it backwards decides every consumer of a node before the node
     * itself — the same traversal order [GatheringPlan.feeders] uses, for the same reason.
     */
    private fun coveredBy(plan: GatheringPlan, direct: Set<String>): Set<String> {
        val consumers = HashMap<String, MutableList<String>>()
        for ((id, node) in plan.nodes) {
            for (req in node.requires) {
                if (req.itemId != id && req.itemId in plan.nodes) {
                    consumers.getOrPut(req.itemId) { mutableListOf() }.add(id)
                }
            }
        }

        val covered = HashSet(direct)
        for (activity in plan.activityList.asReversed()) {
            val id = activity.item.id
            if (id in covered) continue
            // No consumers means nothing above it disappeared — a target in its own right, or a
            // node the plan keeps for another reason. Not covered by implication.
            val itsConsumers = consumers[id] ?: continue
            if (itsConsumers.isNotEmpty() && itsConsumers.all { it in covered }) covered.add(id)
        }
        return covered
    }
}

/**
 * Why one design in a choice leads the others (MCO-483).
 *
 * The recommendation has to be *stated on the row*: `71k Ice Farm` against `72k Ice Farm` is a
 * 1.4% difference, and a user who cannot see what ranked them has been given an arbitrary answer
 * dressed as advice.
 */
sealed interface RecommendationReason {

    /** One design covers this demand. There is no choice, and nothing to explain. */
    data object Sole : RecommendationReason

    /**
     * Covers the demand soonest. [runnerUpHours] is the next best time, so the row can show the
     * gap rather than assert a winner — 5.7h against 5.8h is a coin toss and should look like one.
     */
    data class Fastest(val hours: Double, val runnerUpHours: Double) : RecommendationReason

    /** The only design here whose author measured a rate. Not "the best" — the only comparable. */
    data class OnlyMeasured(val hours: Double) : RecommendationReason

    /**
     * Nothing separates them on speed: either no design here has a measured rate ([hours] null)
     * or they all cover it in the same time. The order is then alphabetical, and the row says so
     * instead of implying a judgement it did not make.
     */
    data class NoFasterOption(val hours: Double?) : RecommendationReason
}

/**
 * Designs that answer the same demand, as one choice rather than several rows (MCO-483).
 *
 * The YAMS plan listed a *71k Ice Farm* and a *72k Ice Farm* as peer rows with a checkbox each,
 * both claiming the same 20,611 Ice, under a button reading "Review selected designs". Nothing on
 * screen said they were the same job, so a panel showing three farms and a choice read as a
 * shopping list of four farms to build.
 *
 * ## The grouping key is the set of demanded items a design directly produces
 *
 * Two designs are the same job when they answer exactly the same demand lines. Ice against Ice is
 * the easy case and the one that shipped this. The key is a **set**, not a single item id, so the
 * harder case has somewhere to go: a design covering Ice *and* Packed Ice keys differently from
 * one covering only Ice, and today those stay separate rows — the honest answer while nothing here
 * can rank partial coverage against total coverage. When that case lands it lands as a *merge of
 * intersecting keys* (connected components over these sets), not as a different key, and neither
 * the row shape nor the one-of-N selection rule changes.
 *
 * [FarmSuggestion.alsoRemoves] is deliberately not part of the key. It is derived from the direct
 * set and the plan, so two designs with the same key always have the same knock-on; folding it in
 * could only invent a way for identical designs to key apart.
 *
 * ## The ranking signal is time to cover, and it is rendered
 *
 * Within a group every design removes the same units — [FarmSuggestion.unitsRemoved], which ranks
 * the groups, cannot separate them by construction. What differs is how fast: the recommendation
 * is the design that covers the demand soonest ([FarmSuggestion.coverageHours]), measured rates
 * ahead of unmeasured ones, ties broken by name so the order is stable across reloads.
 * [RecommendationReason] carries that judgement to the row, because a ranking the user cannot see
 * is a ranking they cannot disagree with.
 */
data class FarmSuggestionChoice(
    /** The demand every design here answers — the group's identity. */
    val coveredItemIds: Set<String>,
    val recommended: FarmSuggestion,
    /** The rest, best first. Empty for a demand only one design covers. */
    val alternatives: List<FarmSuggestion>,
    val reason: RecommendationReason,
) {
    /** Every design in this choice, recommendation first. */
    val designs: List<FarmSuggestion> = listOf(recommended) + alternatives

    /**
     * A stable handle for the group, so the page can scope "pick one of these" to it.
     *
     * Derived from the covered items rather than from a position in the list: the same choice
     * keeps the same handle when a design joins the bank or the plan's quantities move.
     */
    val key: String = coveredItemIds.sorted().joinToString("+")
}

/** Groups [FarmSuggestions] output into choices — see [FarmSuggestionChoice]. */
object FarmSuggestionChoices {

    /**
     * [suggestions] grouped by the demand they cover, most work removed first.
     *
     * Group order is the order [FarmSuggestions.of] already produces — units removed descending,
     * then name — read off each group's recommendation. Grouping changes which design speaks for a
     * demand; it does not reorder the demands.
     */
    fun of(suggestions: List<FarmSuggestion>): List<FarmSuggestionChoice> =
        suggestions
            .groupBy { design -> design.produces.mapTo(LinkedHashSet()) { it.itemId } as Set<String> }
            .map { (covered, designs) -> choiceOf(covered, designs) }
            .sortedWith(
                compareByDescending<FarmSuggestionChoice> { it.recommended.unitsRemoved }
                    .thenBy { it.recommended.ideaName }
            )

    private fun choiceOf(covered: Set<String>, designs: List<FarmSuggestion>): FarmSuggestionChoice {
        // Nulls last: an unmeasured design is not the fastest, it is the one nobody timed, and
        // sorting it first would recommend the design with the least evidence behind it.
        val ranked = designs.sortedWith(
            compareBy<FarmSuggestion, Double?>(nullsLast()) { it.coverageHours }.thenBy { it.ideaName }
        )
        val recommended = ranked.first()
        val alternatives = ranked.drop(1)
        return FarmSuggestionChoice(covered, recommended, alternatives, reasonFor(recommended, alternatives))
    }

    private fun reasonFor(
        recommended: FarmSuggestion,
        alternatives: List<FarmSuggestion>,
    ): RecommendationReason {
        if (alternatives.isEmpty()) return RecommendationReason.Sole
        val hours = recommended.coverageHours ?: return RecommendationReason.NoFasterOption(null)
        // Already sorted nulls-last, so the first measured alternative is the best one.
        val runnerUp = alternatives.firstNotNullOfOrNull { it.coverageHours }
            ?: return RecommendationReason.OnlyMeasured(hours)
        return if (runnerUp > hours) RecommendationReason.Fastest(hours, runnerUp)
        else RecommendationReason.NoFasterOption(hours)
    }
}

/**
 * The designs worth building for [plan], or an empty list if there is nothing to suggest.
 *
 * Decorates a plan that has already rendered, so every failure degrades to "no suggestions"
 * rather than failing the page — same posture as [GetFarmScaleThresholdStep] and
 * [pendingFarmSuppliesFor]. A missing suggestion is a worse plan; a missing plan is no page.
 */
suspend fun farmSuggestionsFor(
    plan: GatheringPlan?,
    threshold: Int,
    viewerId: Int,
    projectId: Int,
    /**
     * The not-yet-running farms MCO-299 is about to put a notice on this same page for.
     *
     * Taken as a parameter rather than re-queried on purpose — see [alreadyCoveredItems].
     */
    pendingFarms: List<PendingFarmSupply>,
    /**
     * The design this project was imported from, if any — never suggested back to it.
     *
     * Now largely subsumed by the project's own productions (MCO-458), since importing a design
     * writes `project_productions`. It stays because it is the one exclusion that survives a
     * project whose productions were edited away, and it costs nothing.
     */
    excludeIdeaId: Int? = null,
): List<FarmSuggestion> {
    if (plan == null) return emptyList()

    val alreadyCovered = alreadyCoveredItems(projectId, pendingFarms)

    val demandedIds = plan.activityList
        .filter { it.status != PlanNodeStatus.SUPPLIED && it.status != PlanNodeStatus.OPEN_TAG }
        .map { it.item.id }
        .filter { it !in alreadyCovered }
    if (demandedIds.isEmpty()) return emptyList()

    val producers = GetIdeaProducersStep
        .process(IdeaProducerInput(itemIds = demandedIds, viewerId = viewerId))
        .getOrNull()
        ?: return emptyList()

    return FarmSuggestions.of(
        plan,
        threshold,
        producers.filter { it.ideaId != excludeIdeaId },
        alreadyCovered,
    )
}

/**
 * Items this world is already going to produce, and so has no reason to be offered a design for.
 *
 * Two sources, one rule:
 *
 * * **This project's own productions** (MCO-458). A farm's build materials are demand like any
 *   other, and a cobblestone farm costs cobblestone. Excluding the project's source design by id
 *   closes the import door only: a farm recorded through MCO-298 has no `project_idea_id` to
 *   exclude, and its productions are the thing that actually identifies what it makes.
 * * **Farms planned but not running** (MCO-461), read off the notice MCO-299 already renders
 *   rather than queried again. Sharing the value is the point — the notice and the suggestion
 *   list cannot claim the same item while both are computed from one list, whereas two
 *   independent reads of [GetWorldPlannedFarmsStep] could drift and put contradictory advice on
 *   one page.
 *
 * Operational (`DONE`) farms need no entry here: MCO-287 already marks their items
 * [PlanNodeStatus.SUPPLIED], which [FarmSuggestions] drops.
 *
 * A failed read of this project's productions degrades to excluding nothing — the same posture as
 * every other decoration on the plan, and the reason this is not the only guard on the import.
 */
private suspend fun alreadyCoveredItems(
    projectId: Int,
    pendingFarms: List<PendingFarmSupply>,
): Set<String> = buildSet {
    GetResourceProductionStep.process(projectId).getOrNull().orEmpty().mapTo(this) { it.itemId }
    pendingFarms.flatMapTo(this) { farm -> farm.items.map { it.itemId } }
}
