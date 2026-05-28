package app.mcorg.pipeline.project

import app.mcorg.pipeline.Result
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.project.commonsteps.SetViewPreferenceInput
import app.mcorg.pipeline.project.commonsteps.SetViewPreferenceStep
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.pipeline.task.SearchTasksInput
import app.mcorg.pipeline.task.SearchTasksStep
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.dsl.pages.executeViewFragment
import app.mcorg.presentation.templated.dsl.pages.planViewFragment
import app.mcorg.presentation.templated.dsl.pages.toggleOobFragments
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.handleGetDetailContent() {
    val user = getUser()
    val worldId = getWorldId()
    val projectId = getProjectId()
    val view = request.queryParameters["view"]
        ?.takeIf { it == "plan" || it == "execute" }
        ?: "execute"

    // Persist preference
    SetViewPreferenceStep.process(SetViewPreferenceInput(user.id, projectId, view))

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

    if (view == "plan") {
        respondHtml(planViewFragment(project, resources, tasks) + toggleOobFragments(worldId, projectId, "plan"))
        return
    }

    respondHtml(executeViewFragment(project, resources, tasks) + toggleOobFragments(worldId, projectId, "execute"))
}
