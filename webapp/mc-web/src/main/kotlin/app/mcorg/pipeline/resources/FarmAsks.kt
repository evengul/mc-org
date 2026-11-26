package app.mcorg.pipeline.resources

import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.project.commonsteps.GetFarmSupplyEdgesStep
import app.mcorg.pipeline.resources.commonsteps.GetProgressForProjectStep

/** What one plan still asks of a farm for one item. */
data class PlanAsk(val projectId: Int, val projectName: String, val left: Long)

/** One item a mode makes for the world's plans, at the mode's rate for it. */
data class ItemAsk(
    val itemId: String,
    val itemName: String,
    val ratePerHour: Int,
    val asks: List<PlanAsk>,
) {
    val left: Long get() = asks.sumOf { it.left }
    val hoursLeft: Double? get() = if (ratePerHour > 0) left.toDouble() / ratePerHour else null
}

/**
 * What the world's plans ask of one of a farm's modes, summed over the plans.
 *
 * Like a plan's [ModeRun], the mode runs as long as its longest-needed item: items it makes
 * together come out of the same run.
 */
data class ModeAsk(
    val modeId: Int?,
    val modeName: String?,
    val modePosition: Int,
    val items: List<ItemAsk>,
) {
    val setBy: ItemAsk? get() = items.filter { it.hoursLeft != null }.maxByOrNull { it.hoursLeft!! }
    val hoursLeft: Double? get() = setBy?.hoursLeft
    val isToken: Boolean get() = hoursLeft?.let { it < FarmRuns.TOKEN_HOURS } ?: false
    val planIds: Set<Int> get() = items.flatMap { it.asks }.mapTo(mutableSetOf()) { it.projectId }
}

/**
 * What a farm's page says to set it to (MCO-605, design frame 2a·C): each mode the world's plans
 * ask for, how long, and for which plans.
 *
 * @param asked the modes asked for, in the farm's own order; [FarmAsks.of] assigns each plan's lines
 *   to modes the way that plan's own Collect section does ([FarmRuns.derive]), so the farm page and
 *   the plans agree on which mode makes what.
 * @param rates every mode's rates, asked for or not, for the list of every mode.
 */
data class FarmAsks(
    val farmId: Int,
    val asked: List<ModeAsk>,
    val rates: List<FarmModeRate>,
) {
    val farmModeCount: Int get() = rates.mapNotNull { it.modeId }.distinct().size
    val isEmpty: Boolean get() = asked.isEmpty()

    companion object {

        /** A plan that is finished, stopped or archived asks nothing, whatever its cached demand says. */
        private val NOT_ASKING = setOf(
            ProjectState.DONE, ProjectState.CANCELLED, ProjectState.ARCHIVED, ProjectState.DECOMMISSIONED,
        )

        /**
         * Sum each plan's runs into the farm's modes. Pure.
         *
         * @param edges the world's farm supply edges, as the roadmap reads them: one per plan and
         *   item, already narrowed to the farm each plan's line comes from (MCO-603).
         * @param progress what each plan has logged, by plan and item.
         */
        fun of(
            farmId: Int,
            farmName: String,
            edges: List<ProjectResourceEdge>,
            progress: Map<Int, Map<String, Int>>,
            rates: List<FarmModeRate>,
        ): FarmAsks {
            val mine = edges.filter { it.producerId == farmId && it.consumerState !in NOT_ASKING && it.quantity != null }
            val producer = rates.groupBy { it.itemId }.mapValues { (itemId, r) ->
                FarmSupplyRow(itemId, farmId, farmName, r.maxOf { it.ratePerHour })
            }

            // Plan by plan, so an item rides along on a mode that plan already runs, as on its page.
            val perPlan = mine.groupBy { it.consumerId }.map { (planId, planEdges) ->
                val logged = progress[planId].orEmpty()
                val lines = planEdges.map { edge ->
                    SuppliedLine(
                        itemId = edge.itemId!!,
                        itemName = edge.itemName ?: edge.itemId!!,
                        quantity = edge.quantity!!,
                        logged = (logged[edge.itemId] ?: 0).toLong().coerceIn(0, edge.quantity!!),
                    )
                }.filterNot { it.isDone }
                Triple(planId, planEdges.first().consumerName, FarmRuns.derive(lines, producer, rates).singleOrNull())
            }

            val byMode = linkedMapOf<Int?, MutableList<Triple<Int, String, ModeRun>>>()
            perPlan.forEach { (planId, planName, run) ->
                run?.modes?.forEach { mode -> byMode.getOrPut(mode.modeId) { mutableListOf() }.add(Triple(planId, planName, mode)) }
            }

            val asked = byMode.map { (modeId, entries) ->
                val mode = entries.first().third
                val items = entries
                    .flatMap { (planId, planName, m) -> m.items.map { Triple(planId, planName, it) } }
                    .groupBy { it.third.line.itemId }
                    .map { (itemId, byPlan) ->
                        ItemAsk(
                            itemId = itemId,
                            itemName = byPlan.first().third.line.itemName,
                            ratePerHour = byPlan.first().third.ratePerHour,
                            asks = byPlan
                                .map { (planId, planName, item) -> PlanAsk(planId, planName, item.line.left) }
                                .sortedByDescending { it.left },
                        )
                    }
                    .sortedByDescending { it.left }
                ModeAsk(modeId, mode.modeName, mode.modePosition, items)
            }.sortedBy { it.modePosition }

            return FarmAsks(farmId, asked, rates)
        }

        /**
         * Load and sum what the world's plans ask of [farmId].
         *
         * Reads the roadmap's cached demand (`project_demand`, through [GetFarmSupplyEdgesStep])
         * rather than deriving every plan in the world per page view: production answers every
         * request on one thread, and a derivation is the expensive part of a page. A plan nobody has
         * opened since its inputs changed is read as it was last derived, as on the roadmap.
         */
        suspend fun load(worldId: Int, farmId: Int, farmName: String): Result<AppFailure, FarmAsks> {
            val edges = when (val r = GetFarmSupplyEdgesStep(worldId).process(Unit)) {
                is Result.Success -> r.value.filter { it.producerId == farmId }
                is Result.Failure -> return r
            }
            if (edges.isEmpty()) return Result.success(FarmAsks(farmId, emptyList(), emptyList()))
            val rates = when (val r = GetFarmModeRatesStep.process(setOf(farmId))) {
                is Result.Success -> r.value
                is Result.Failure -> return r
            }
            val progress = edges.map { it.consumerId }.distinct().associateWith { planId ->
                when (val r = GetProgressForProjectStep.process(planId)) {
                    is Result.Success -> r.value
                    is Result.Failure -> return r
                }
            }
            return Result.success(of(farmId, farmName, edges, progress, rates))
        }
    }
}
