package app.mcorg.pipeline.project

import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.project.commonsteps.GetViewPreferenceInput
import app.mcorg.pipeline.project.commonsteps.GetViewPreferenceStep
import app.mcorg.pipeline.resources.GatheringPlanInput
import app.mcorg.pipeline.resources.GenerateGatheringPlanStep
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.pipeline.resources.commonsteps.GetProgressForProjectStep
import app.mcorg.pipeline.task.SearchTasksInput
import app.mcorg.pipeline.task.SearchTasksStep
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.dsl.pages.projectDetailPage
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.getWorldName
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleGetProject() {
    val user = getUser()
    val worldId = getWorldId()
    val projectId = getProjectId()

    val project = when (val result = GetProjectByIdStep.process(projectId)) {
        is Result.Success -> result.value
        is Result.Failure -> {
            defaultHandleError(result.error)
            return
        }
    }

    // Resolve lens from saved view preference (old "plan"/"execute" values map to "list")
    val savedView = GetViewPreferenceStep.process(GetViewPreferenceInput(user.id, projectId))
        .getOrNull() ?: "list"
    val lens = when (savedView) {
        "plan", "execute", "list" -> "list"
        "next", "sessions" -> savedView
        else -> "list"
    }

    val resources = GetAllResourceGatheringItemsStep.process(projectId).getOrNull() ?: emptyList()

    val tasks = when (val result = SearchTasksStep(projectId).process(SearchTasksInput(completionStatus = "ALL"))) {
        is Result.Success -> result.value
        is Result.Failure -> {
            respondBadRequest("Failed to load tasks")
            return
        }
    }

    // Derive the gathering plan — failure is non-fatal (renders fallback state)
    val plan = when (val result = GenerateGatheringPlanStep.process(GatheringPlanInput(projectId, worldId))) {
        is Result.Success -> result.value
        is Result.Failure -> when (result.error) {
            is AppFailure.ValidationError -> null
            is AppFailure.DatabaseError.NotFound -> null
            else -> {
                defaultHandleError(result.error)
                return
            }
        }
    }

    val isAdmin = ValidateWorldMemberRole<Unit>(user, Role.ADMIN, worldId).process(Unit) is Result.Success

    val worldName = getWorldName(worldId)

    // Load persisted progress for all items in the project (covers derived activities too)
    val progressMap = GetProgressForProjectStep.process(projectId).getOrNull() ?: emptyMap()

    respondHtml(projectDetailPage(user, project, worldName, resources, tasks, lens, isWorldAdmin = isAdmin, plan = plan, progressMap = progressMap))
}
