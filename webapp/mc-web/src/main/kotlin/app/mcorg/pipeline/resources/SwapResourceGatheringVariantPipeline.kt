package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.pipeline.Quadruple
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.commonsteps.GetProjectsInWorldStep
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.pipeline.resources.commonsteps.GetResourceGatheringItemStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.planResourcesAreaFragment
import app.mcorg.presentation.templated.dsl.pages.resourceDetailPanelOobFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters

/**
 * MCO-246: swaps a target's item to a chosen variant — another member of a tag family the
 * current item belongs to (e.g. birch planks -> spruce planks via `#minecraft:planks`; see
 * [findVariantCandidates]). Updates the `resource_gathering` row's `item_id` and `name` in
 * place; the gathering plan re-derives from the new item on the next read
 * ([GenerateGatheringPlanStep] — nothing is persisted beyond the swap itself).
 *
 * Candidates are recomputed server-side from the item's *current* value (not trusted from the
 * client), so a `itemId` outside that set — including the item's own current id, an id from an
 * unrelated tag family, or a non-existent item — is rejected as a validation failure.
 *
 * Responds with the whole `#plan-resources-area` fragment (its row's name/qty display changes)
 * plus an out-of-band refresh of the open resource-detail panel, if any, so a swap made from
 * within the panel reflects the new item/variant list without needing to reopen it.
 */
suspend fun ApplicationCall.handleSwapResourceGatheringVariant() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val resourceGatheringId = getResourceGatheringId()
    val parameters = receiveParameters()

    handlePipeline(
        onSuccess = { (resources, panelResource, projectsInWorld, panelCandidates) ->
            respondHtml(
                planResourcesAreaFragment(worldId, projectId, resources) +
                    resourceDetailPanelOobFragment(worldId, projectId, panelResource, projectsInWorld, panelCandidates)
            )
        }
    ) {
        val current = GetResourceGatheringItemStep.run(resourceGatheringId)
        val graph = getGraphForWorld(worldId)
        val candidates = findVariantCandidates(graph, current.itemId)
        val chosen = ValidateSwapVariantInputStep(candidates).run(parameters)
        SwapResourceGatheringVariantStep(resourceGatheringId).run(chosen)

        val updated = GetResourceGatheringItemStep.run(resourceGatheringId)
        val updatedCandidates = findVariantCandidates(graph, updated.itemId)
        val resources = GetAllResourceGatheringItemsStep.run(projectId)
        val projectsInWorld = GetProjectsInWorldStep(projectId).run(worldId)
        Quadruple(resources, updated, projectsInWorld, updatedCandidates)
    }
}

internal data class ValidateSwapVariantInputStep(
    val candidates: List<Item>,
) : Step<Parameters, AppFailure.ValidationError, Item> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Item> {
        val itemId = input["itemId"]
        return when {
            itemId.isNullOrBlank() -> Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.MissingParameter("itemId")))
            )
            candidates.none { it.id == itemId } -> Result.failure(
                AppFailure.ValidationError(
                    listOf(ValidationFailure.InvalidValue("itemId", candidates.map { it.id }))
                )
            )
            else -> Result.success(candidates.first { it.id == itemId })
        }
    }
}

private data class SwapResourceGatheringVariantStep(val resourceGatheringId: Int) :
    Step<Item, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Item): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<Item>(
            sql = SafeSQL.update("UPDATE resource_gathering SET item_id = ?, name = ? WHERE id = ?"),
            parameterSetter = { statement, item ->
                statement.setString(1, item.id)
                statement.setString(2, item.name)
                statement.setInt(3, resourceGatheringId)
            }
        ).process(input).map { }
    }
}
