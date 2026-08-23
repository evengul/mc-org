package app.mcorg.pipeline.resources

import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus

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
     */
    fun of(plan: GatheringPlan, threshold: Int, producers: List<IdeaProducer>): List<FarmSuggestion> {
        if (producers.isEmpty()) return emptyList()

        val demand = matchableDemand(plan)
        if (demand.isEmpty()) return emptyList()

        return producers
            .mapNotNull { producer -> producer.suggestionFor(plan, demand, threshold) }
            .sortedWith(compareByDescending<FarmSuggestion> { it.unitsRemoved }.thenBy { it.ideaName })
    }

    /** itemId -> the demand a design could answer. */
    private fun matchableDemand(plan: GatheringPlan): Map<String, DemandRow> =
        plan.activityList
            .filter { it.status != PlanNodeStatus.SUPPLIED && it.status != PlanNodeStatus.OPEN_TAG }
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
 * The designs worth building for [plan], or an empty list if there is nothing to suggest.
 *
 * Decorates a plan that has already rendered, so every failure degrades to "no suggestions"
 * rather than failing the page — same posture as [GetFarmScaleThresholdStep] and
 * [pendingFarmSuppliesFor]. A missing suggestion is a worse plan; a missing plan is no page.
 */
suspend fun farmSuggestionsFor(plan: GatheringPlan?, threshold: Int, viewerId: Int): List<FarmSuggestion> {
    if (plan == null) return emptyList()

    val demandedIds = plan.activityList
        .filter { it.status != PlanNodeStatus.SUPPLIED && it.status != PlanNodeStatus.OPEN_TAG }
        .map { it.item.id }
    if (demandedIds.isEmpty()) return emptyList()

    val producers = GetIdeaProducersStep
        .process(IdeaProducerInput(itemIds = demandedIds, viewerId = viewerId))
        .getOrNull()
        ?: return emptyList()

    return FarmSuggestions.of(plan, threshold, producers)
}
