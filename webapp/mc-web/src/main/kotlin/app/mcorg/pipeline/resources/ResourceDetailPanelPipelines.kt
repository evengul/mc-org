package app.mcorg.pipeline.resources

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.commonsteps.GetProjectsInWorldStep
import app.mcorg.pipeline.resources.commonsteps.ClearResourceSourceStep
import app.mcorg.pipeline.resources.commonsteps.GetResourceGatheringItemStep
import app.mcorg.pipeline.resources.commonsteps.ResourceSourceAssignment
import app.mcorg.pipeline.resources.commonsteps.SetResourceSourceStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.resourceDetailPanelFragment
import app.mcorg.presentation.templated.dsl.pages.resourcePanelSourceWithRowOob
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters

suspend fun ApplicationCall.handleGetResourceDetailPanel() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val resourceGatheringId = getResourceGatheringId()

    // Version scopes the "Replace item" search combo (see resourcePanelVariantSection) to the
    // project's Minecraft version; null falls back to an unscoped search (validation still
    // enforces the version catalog server-side).
    val version = GetWorldVersionStep.process(worldId).getOrNull()

    handlePipeline(
        onSuccess = { (resource, projects, suggestions) ->
            respondHtml(resourceDetailPanelFragment(worldId, projectId, resource, projects, suggestions, version))
        }
    ) {
        val resource = GetResourceGatheringItemStep.run(resourceGatheringId)
        val projects = GetProjectsInWorldStep(projectId).run(worldId)
        val graph = getGraphForWorld(worldId)
        val suggestions = findVariantCandidates(graph, resource.itemId)
        Triple(resource, projects, suggestions)
    }
}

suspend fun ApplicationCall.handleSetResourceSource() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val resourceGatheringId = getResourceGatheringId()
    val parameters = receiveParameters()

    handlePipeline(
        onSuccess = { (resource, projects) ->
            respondHtml(resourcePanelSourceWithRowOob(worldId, projectId, resource, projects))
        }
    ) {
        val projects = GetProjectsInWorldStep(projectId).run(worldId)
        val assignment = ValidateResourceSourceInputStep(projects).run(parameters)
        SetResourceSourceStep(resourceGatheringId).run(assignment)
        val resource = GetResourceGatheringItemStep.run(resourceGatheringId)
        resource to projects
    }
}

suspend fun ApplicationCall.handleClearResourceSource() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val resourceGatheringId = getResourceGatheringId()

    handlePipeline(
        onSuccess = { (resource, projects) ->
            respondHtml(resourcePanelSourceWithRowOob(worldId, projectId, resource, projects))
        }
    ) {
        ClearResourceSourceStep(resourceGatheringId).run(Unit)
        val resource = GetResourceGatheringItemStep.run(resourceGatheringId)
        val projects = GetProjectsInWorldStep(projectId).run(worldId)
        resource to projects
    }
}

internal data class ValidateResourceSourceInputStep(
    val projectsInWorld: List<Pair<Int, String>>,
) : Step<Parameters, AppFailure.ValidationError, ResourceSourceAssignment> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, ResourceSourceAssignment> {
        val type = input["type"]
        return when (type) {
            "manual" -> Result.success(ResourceSourceAssignment.Manual)
            "project" -> {
                val projectIdRaw = input["projectId"]
                val projectId = projectIdRaw?.toIntOrNull()
                    ?: return Result.failure(
                        AppFailure.ValidationError(
                            listOf(ValidationFailure.MissingParameter("projectId"))
                        )
                    )
                if (projectsInWorld.none { it.first == projectId }) {
                    return Result.failure(
                        AppFailure.ValidationError(
                            listOf(
                                ValidationFailure.InvalidValue(
                                    "projectId",
                                    projectsInWorld.map { it.first.toString() },
                                )
                            )
                        )
                    )
                }
                Result.success(ResourceSourceAssignment.LinkedProject(projectId))
            }
            null, "" -> Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.MissingParameter("type")))
            )
            else -> Result.failure(
                AppFailure.ValidationError(
                    listOf(ValidationFailure.InvalidValue("type", listOf("manual", "project")))
                )
            )
        }
    }
}
