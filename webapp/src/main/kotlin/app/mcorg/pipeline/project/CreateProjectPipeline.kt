package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.CreateProjectFailures
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.world.projectItem
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.li
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleCreateProject() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val worldId = this.getWorldId()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                projectItem(it)
            } + createHTML().div {
                hxOutOfBands("delete:#empty-projects-state")
            })
        },
        onFailure = { failure ->
            val errorMessage = when (failure) {
                is CreateProjectFailures.ValidationError -> "Invalid project data: ${failure.errors.joinToString(", ")}"
                is CreateProjectFailures.DatabaseError -> "Unable to create project: Database error"
                is CreateProjectFailures.WorldNotFound -> "World not found"
                is CreateProjectFailures.InsufficientPermissions -> "You don't have permission to create projects in this world"
            }
            respondBadRequest(errorMessage)
        }
    ) {
        step(Step.value(parameters))
            .step(ValidateProjectInputStep)
            .step(ValidateWorldAdminStep(user, worldId))
            .step(CreateProjectStep(worldId))
            .step(GetProjectAfterCreation)
    }
}
