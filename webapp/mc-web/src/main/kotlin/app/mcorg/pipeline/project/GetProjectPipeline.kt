package app.mcorg.pipeline.project

import app.mcorg.pipeline.Result
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.project.commonsteps.GetViewPreferenceInput
import app.mcorg.pipeline.project.commonsteps.GetViewPreferenceStep
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.pipeline.task.SearchTasksInput
import app.mcorg.pipeline.task.SearchTasksStep
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.dsl.pages.projectDetailPage
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
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

    val view = GetViewPreferenceStep.process(GetViewPreferenceInput(user.id, projectId))
        .getOrNull() ?: "execute"

    val resources = if (view == "execute") {
        GetAllResourceGatheringItemsStep.process(projectId).getOrNull() ?: emptyList()
    } else {
        emptyList()
    }

    val tasks = if (view == "execute") {
        when (val result = SearchTasksStep(projectId).process(SearchTasksInput(completionStatus = "ALL"))) {
            is Result.Success -> result.value
            is Result.Failure -> {
                respondBadRequest("Failed to load tasks")
                return
            }
        }
    } else {
        emptyList()
    }

    respondHtml(projectDetailPage(user, project, resources, tasks, view))
}
