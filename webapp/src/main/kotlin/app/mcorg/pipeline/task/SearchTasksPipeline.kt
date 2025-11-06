package app.mcorg.pipeline.task

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.tasksList
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.ul

suspend fun ApplicationCall.handleSearchTasks() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val parameters = this.parameters

    executePipeline(
        onSuccess = {
            val mainContent = createHTML().ul {
                tasksList(worldId, projectId, it)
            }

            if (it.isEmpty()) {
                respondHtml(mainContent + createHTML().p {
                    hxOutOfBands("innerHTML:#no-tasks-found")
                    + "No tasks found matching the search criteria."
                })
            } else {
                respondHtml(mainContent + createHTML().p {
                    hxOutOfBands("innerHTML:#no-tasks-found")
                    style = "display:none;"
                    + ""
                })
            }
        },
        onFailure = { respond(HttpStatusCode.InternalServerError) }
    ) {
        value(parameters)
            .step(ValidateSearchTasksInputStep)
            .step(SearchTasksStep(projectId))
    }
}

object ValidateSearchTasksInputStep : Step<Parameters, AppFailure.ValidationError, SearchTasksInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, SearchTasksInput> {
        val query = input["query"]?.takeIf { it.isNotBlank() }
        val completionStatus = input["completionStatus"]?.takeIf { it in listOf("ALL", "IN_PROGRESS", "COMPLETED") } ?: "IN_PROGRESS"
        val priority = input["priority"]?.takeIf {
            it == "ALL" || Priority.entries.any { priority -> priority.name == it }
        } ?: "ALL"
        val stage = input["stage"]?.takeIf {
            it == "ALL" || ProjectStage.entries.any { stage -> stage.name == it }
        } ?: "ALL"

        val sortBy = input["sortBy"]?.takeIf { it.isNotBlank() && it in listOf("name_asc", "lastModified_desc", "priority_asc") } ?: "priority_desc"

        return Result.Success(
            SearchTasksInput(
                query = query,
                completionStatus = completionStatus,
                priority = priority,
                stage = stage,
                sortBy = sortBy
            )
        )
    }
}