package app.mcorg.pipeline.resources

import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.model.resources.ResourceSourceType
import app.mcorg.engine.plan.SupplySource
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep

/**
 * What a project already has supplied to it — world farms and linked projects, folded into the
 * one map the engine takes.
 *
 * This lived inside [GenerateGatheringPlanStep.process] and was thrown away with the plan, which
 * meant the *plan* knew about the project's farms and the drill's source **picker did not**
 * (MCO-523). Under the cost model that is not a cosmetic difference: a supplied item costs 0 and
 * that propagates to every price downstream of it, so the picker was quoting unsupplied prices and
 * could rank two candidates in the other order — marking "quickest ★" on a source the plan had not
 * chosen, which is precisely the drift MCO-521 was filed to make impossible.
 *
 * So the rule has one home. [fold] is the rule itself and holds no queries, so the plan step keeps
 * using the rows it has already loaded rather than fetching them twice; [load] is for callers that
 * hold nothing yet. Both produce the same map, which is what lets `PlanCostModel` hand the two
 * paths the *same cached model* rather than two models that merely agree.
 */
object ProjectSupply {

    /**
     * Fold already-loaded rows into the supplied map. Pure.
     *
     * World farm supply first, explicit choices on top. Operational (DONE) projects' productions
     * supply the whole world (MCO-296): any item they produce terminates as a SUPPLIED leaf
     * wherever it appears in a chain. An explicit `manual` source pick opts the item out of farm
     * supply; an explicit project link replaces the farm entry through the union.
     *
     * @param activeItems the project's resource rows **with ignored ones already removed** — an
     *   ignored row stays in `resource_gathering` but must not feed the plan (MCO-247).
     */
    fun fold(
        activeItems: List<ResourceGatheringItem>,
        farms: List<FarmSupplyRow>,
    ): Map<String, SupplySource> {
        val manualItems: Set<String> = activeItems
            .filter { it.sourceType == ResourceSourceType.MANUAL }
            .map { it.itemId }
            .toSet()
        val farmSupplied: Map<String, SupplySource> = farms
            .filter { it.itemId !in manualItems }
            .groupBy { it.itemId }
            .mapValues { (_, producers) -> SupplySource.Farm(producers.first().projectName) }
        val linkedSupplied: Map<String, SupplySource> = activeItems
            .mapNotNull { item ->
                val (solvedId, solvedName) = item.solvedByProject ?: return@mapNotNull null
                item.itemId to SupplySource.LinkedProject(solvedId, solvedName)
            }
            .toMap()
        return farmSupplied + linkedSupplied
    }

    /**
     * Load the rows [fold] needs and fold them — for callers that are not already holding them.
     *
     * The picker path uses this. It costs two indexed queries, against a ~100 ms whole-graph
     * relaxation that it lets us *avoid* by hitting the same `PlanCostModel` cache entry the plan
     * built, so it pays for itself the moment a plan has been derived for the project.
     */
    suspend fun load(projectId: Int, worldId: Int): Result<AppFailure, Map<String, SupplySource>> {
        val items = when (val r = GetAllResourceGatheringItemsStep.process(projectId)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        val farms = when (
            val r = GetWorldFarmSuppliesStep.process(
                WorldFarmSuppliesInput(worldId = worldId, excludeProjectId = projectId)
            )
        ) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        return Result.success(fold(items.filterNot { it.ignored }, farms))
    }
}
