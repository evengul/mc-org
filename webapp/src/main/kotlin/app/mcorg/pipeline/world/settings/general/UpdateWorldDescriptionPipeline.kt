package app.mcorg.pipeline.world.settings.general

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.layout.alert.AlertType
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.html.li
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateWorldDescription() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                createAlert(
                    id = "world-description-updated-success-alert",
                    type = AlertType.SUCCESS,
                    title = "World Description Updated",
                    autoClose = true
                )
            })
        },
        onFailure = { respond(HttpStatusCode.InternalServerError) }
    ) {
        value(parameters)
            .step(ValidateWorldDescriptionInputStep)
            .step(UpdateWorldDescriptionStep(worldId))
    }
}

object ValidateWorldDescriptionInputStep : Step<Parameters, AppFailure.ValidationError, String> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, String> {
        val descriptionValidation = input["description"]?.let { receivedDescription ->
            ValidationSteps.validateLength("description", maxLength = 1000) { it }.process(receivedDescription.trim())
        }

        return if (descriptionValidation != null && descriptionValidation is Result.Failure) {
            Result.failure(AppFailure.ValidationError(listOf(descriptionValidation.error)))
        } else {
            val description = descriptionValidation?.getOrNull() ?: ""
            Result.success(description.trim())
        }
    }
}

data class UpdateWorldDescriptionStep(val worldId: Int) : Step<String, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: String): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<String>(
            SafeSQL.update("""
                UPDATE world 
                SET description = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            """),
            parameterSetter = { statement, description ->
                statement.setString(1, description)
                statement.setInt(2, worldId)
            }
        ).process(input).map { worldId }
    }
}
