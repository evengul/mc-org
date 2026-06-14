package app.mcorg.pipeline.project

import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.resources.GatheringPlanInput
import app.mcorg.pipeline.resources.GenerateGatheringPlanStep
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.pipeline.resources.commonsteps.GetProgressForProjectStep
import app.mcorg.pipeline.task.SearchTasksInput
import app.mcorg.pipeline.task.SearchTasksStep
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.dsl.pages.gatheringPlannerFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleGetDetailContent() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    // The retired PLAN/EXECUTE toggle's old ?view= links resolve to the default List lens.
    val lens = request.queryParameters["lens"]
        ?.takeIf { it == "list" || it == "next" || it == "sessions" }
        ?: "list"

    val project = when (val result = GetProjectByIdStep.process(projectId)) {
        is Result.Success -> result.value
        is Result.Failure -> {
            defaultHandleError(result.error)
            return
        }
    }

    val resources = GetAllResourceGatheringItemsStep.process(projectId).getOrNull() ?: emptyList()
    val tasks = when (val result = SearchTasksStep(projectId).process(SearchTasksInput(completionStatus = "ALL"))) {
        is Result.Success -> result.value
        is Result.Failure -> {
            respondBadRequest("Failed to load tasks")
            return
        }
    }

    // Derive the gathering plan — failure is non-fatal. A null plan renders the
    // definition/empty fallback state instead of grouped activity sections.
    val plan = when (val result = GenerateGatheringPlanStep.process(GatheringPlanInput(projectId, worldId))) {
        is Result.Success -> result.value
        is Result.Failure -> when (result.error) {
            // No positive targets (nothing defined yet, or all collected).
            is AppFailure.ValidationError -> null
            // World's Minecraft version has no ingested graph yet.
            is AppFailure.DatabaseError.NotFound -> null
            // Unexpected error — surface it
            else -> {
                defaultHandleError(result.error)
                return
            }
        }
    }

    // Load persisted progress for all items in the project (covers derived activities too)
    val progressMap = GetProgressForProjectStep.process(projectId).getOrNull() ?: emptyMap()

    respondHtml(gatheringPlannerFragment(project, resources, tasks, plan, lens, progressMap))
}
