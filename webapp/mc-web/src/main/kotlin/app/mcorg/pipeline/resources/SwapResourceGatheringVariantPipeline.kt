package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.commonsteps.GetProjectsInWorldStep
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
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
 * MCO-246: swaps a target's item to ANY chosen item valid for the project's Minecraft version —
 * not just a same-tag-family sibling. The user picks either from the tag-family quick-swap
 * suggestions ([findVariantCandidates]) or by free-text search over the whole catalog
 * (`/items/search`, wired in the resource-detail panel). Updates the `resource_gathering`
 * row's `item_id` and `name` in place; the gathering plan re-derives from the new item on the
 * next read ([GenerateGatheringPlanStep] — nothing is persisted beyond the swap itself).
 *
 * The chosen `itemId` is validated server-side against the project's world-version item catalog
 * ([GetItemsInWorldVersionStep] — the same source [handleCreateResourceGatheringItem] validates
 * against), so an unknown/nonexistent id, or an id not present in this version, is rejected. A
 * missing id is a 400; a present-but-unknown id is a 422. The name is taken from the catalog,
 * never trusted from the client.
 *
 * Responds with the whole `#plan-resources-area` fragment (its row's name display changes) plus
 * an out-of-band refresh of the open resource-detail panel, if any, so a swap made from within
 * the panel reflects the new item and its refreshed suggestions without needing to reopen it.
 */
suspend fun ApplicationCall.handleSwapResourceGatheringVariant() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val resourceGatheringId = getResourceGatheringId()
    val parameters = receiveParameters()

    val version = GetWorldVersionStep.process(worldId).getOrNull()

    handlePipeline(
        onSuccess = { (resources, panelResource, projectsInWorld, panelSuggestions) ->
            respondHtml(
                planResourcesAreaFragment(worldId, projectId, resources) +
                    resourceDetailPanelOobFragment(worldId, projectId, panelResource, projectsInWorld, panelSuggestions, version)
            )
        }
    ) {
        val validItems = GetItemsInWorldVersionStep.run(worldId)
        val chosen = ValidateSwapVariantInputStep(validItems).run(parameters)
        SwapResourceGatheringVariantStep(resourceGatheringId).run(chosen)

        val updated = GetResourceGatheringItemStep.run(resourceGatheringId)
        val graph = getGraphForWorld(worldId)
        val suggestions = findVariantCandidates(graph, updated.itemId)
        val resources = GetAllResourceGatheringItemsStep.run(projectId)
        val projectsInWorld = GetProjectsInWorldStep(projectId).run(worldId)
        SwapResult(resources, updated, projectsInWorld, suggestions)
    }
}

private data class SwapResult(
    val resources: List<ResourceGatheringItem>,
    val updated: ResourceGatheringItem,
    val projectsInWorld: List<Pair<Int, String>>,
    val suggestions: List<Item>,
)

/**
 * Validates the chosen `itemId` against [validItems] — the item catalog for the project's
 * Minecraft version. A missing id is a [ValidationFailure.MissingParameter] (400); a present but
 * unknown id (not in this version's catalog) is a [ValidationFailure.InvalidValue] (422). On
 * success returns the catalog [Item] so the row's name is authoritative, not client-supplied.
 */
internal data class ValidateSwapVariantInputStep(
    val validItems: List<Item>,
) : Step<Parameters, AppFailure.ValidationError, Item> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Item> {
        val itemId = input["itemId"]
        return when {
            itemId.isNullOrBlank() -> Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.MissingParameter("itemId")))
            )
            else -> validItems.firstOrNull { it.id == itemId }
                ?.let { Result.success(it) }
                ?: Result.failure(
                    AppFailure.ValidationError(
                        listOf(ValidationFailure.InvalidValue("itemId", validItems.map { it.id }))
                    )
                )
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
