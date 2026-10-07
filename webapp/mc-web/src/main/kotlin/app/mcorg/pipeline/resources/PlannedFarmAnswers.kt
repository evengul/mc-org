package app.mcorg.pipeline.resources

import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus

/**
 * The farm-scale lines one not-yet-running farm project answers (MCO-542).
 *
 * [makes] are roll-up lines the farm produces outright. [alsoRemoves] are lines it does *not*
 * produce, but which only exist to feed something it does — kept apart so the page can never
 * say an iron farm makes ore.
 */
data class PlannedFarmAnswer(
    val projectId: Int,
    val projectName: String,
    val makes: List<FarmScaleDemand>,
    val alsoRemoves: List<KnockOnDemand>,
) {
    val itemIds: Set<String> = (makes.map { it.itemId } + alsoRemoves.map { it.demand.itemId }).toSet()
}

/** A roll-up line that goes away with a farm, and the names of what the farm makes that it feeds. */
data class KnockOnDemand(val demand: FarmScaleDemand, val feeds: List<String>)

fun List<PlannedFarmAnswer>.answeredIds(): Set<String> = flatMapTo(mutableSetOf()) { it.itemIds }

/**
 * Matches the "Worth a farm" roll-up against the farms this project waits on.
 *
 * ## Why it reads the prerequisites and nothing else
 *
 * The farms come from [prerequisiteFarmsFor] — the list the Prerequisites section renders, which
 * reads the roadmap's own edges. A line is labelled with a farm here exactly when that section
 * says the farm comes first, so the panel and the section cannot disagree about the same item:
 * two independent answers to "is this covered" is the bug MCO-461 was filed for. This function
 * only decides *which roll-up line* a prerequisite lands on, which the edge alone cannot say.
 *
 * ## Knock-on
 *
 * The roll-up is raw-gather leaves; a farm usually makes something one step above them. The YAMS
 * world's iron farm makes Iron Ingot, while the roll-up line is Deepslate Iron Ore. The ore goes
 * away with the farm only because it was mined to be smelted, so it is reported as feeding the
 * ingot, never as made by the farm. Which leaves count is [FarmSuggestions.coveredBy]'s rule, the
 * same one a design's knock-on uses: a node counts only when every consumer of it is covered.
 *
 * ## One line, one farm
 *
 * The roll-up lists each item once. A farm that makes a line outright claims it before another
 * farm's knock-on does; otherwise the first farm in [farms]' order (by name, as the
 * Prerequisites section lists them) wins. The Prerequisites section still names every producer.
 */
object PlannedFarmAnswers {

    fun of(
        plan: GatheringPlan,
        demands: List<FarmScaleDemand>,
        farms: List<PendingFarmSupply>,
    ): List<PlannedFarmAnswer> {
        if (demands.isEmpty() || farms.isEmpty()) return emptyList()

        val demandById = demands.associateBy { it.itemId }
        val claimed = mutableSetOf<String>()

        val direct = farms.associateWith { farm ->
            farm.items.map { it.itemId }
                .filter { id -> plan.nodes[id]?.let { it.status != PlanNodeStatus.SUPPLIED } == true }
                .toSet()
        }

        // Outright production claims first, across every farm, so a knock-on never takes a line
        // some other farm actually makes.
        val makes = farms.associateWith { farm ->
            direct.getValue(farm)
                .mapNotNull { demandById[it] }
                .filter { claimed.add(it.itemId) }
                .sortedByDescending { it.quantity }
        }

        val knockOns = farms.associateWith { farm ->
            val made = direct.getValue(farm)
            if (made.isEmpty()) return@associateWith emptyList()
            FarmSuggestions.coveredBy(plan, made)
                .filter { it !in made }
                .mapNotNull { demandById[it] }
                .filter { claimed.add(it.itemId) }
                .sortedByDescending { it.quantity }
                .map { demand -> KnockOnDemand(demand, feedsOf(plan, demand.itemId, made)) }
        }

        return farms.mapNotNull { farm ->
            val farmMakes = makes.getValue(farm)
            val farmKnockOns = knockOns.getValue(farm)
            if (farmMakes.isEmpty() && farmKnockOns.isEmpty()) null
            else PlannedFarmAnswer(farm.projectId, farm.projectName, farmMakes, farmKnockOns)
        }
    }

    /** The names of the items in [made] whose ingredient chain reaches [itemId]. */
    private fun feedsOf(plan: GatheringPlan, itemId: String, made: Set<String>): List<String> =
        made
            .filter { itemId in ingredientsOf(plan, it) }
            .mapNotNull { plan.nodes[it]?.item?.name }
            .sorted()

    private fun ingredientsOf(plan: GatheringPlan, root: String): Set<String> {
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val node = plan.nodes[queue.removeFirst()] ?: continue
            for (req in node.requires) {
                if (seen.add(req.itemId)) queue.addLast(req.itemId)
            }
        }
        return seen
    }
}
