package app.mcorg.pipeline.world.settings.general

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.icon.iconComponent
import app.mcorg.presentation.templated.layout.alert.AlertType
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.li
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleUpdateWorldName() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                createAlert(
                    id = "world-name-updated-success-alert",
                    type = AlertType.SUCCESS,
                    title = "World Name Updated",
                    autoClose = true
                )
            } + createHTML().button {
                classes += setOf("btn--back", "btn--sm")
                hxOutOfBands("innerHTML:#button-back-button")
                iconComponent(Icons.BACK, size = IconSize.SMALL, color = IconColor.ON_SURFACE)
                + "Back to $it"
            })
        },
    ) {
        value(parameters)
            .step(ValidateWorldNameInputStep)
            .step(UpdateWorldNameStep(worldId))
    }
}

object ValidateWorldNameInputStep : Step<Parameters, AppFailure.ValidationError, String> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, String> {
        val nameValidation = ValidationSteps.required(
            "name"
        ) { AppFailure.ValidationError(listOf(it)) }.process(input)

        // Additional validation for name length and uniqueness constraints
        val lengthValidation = ValidationSteps.validateCustom<AppFailure.ValidationError, String?>(
            "name",
            "World name must be between 3 and 100 characters",
            errorMapper = { AppFailure.ValidationError(listOf(it)) },
            predicate = { it != null && it.length in 3..100 }
        ).process(input["name"])

        val errors = mutableListOf<ValidationFailure>()

        if (nameValidation is Result.Failure) {
            errors.addAll(nameValidation.error.errors)
        }

        if (lengthValidation is Result.Failure) {
            errors.addAll(lengthValidation.error.errors)
        }

        return if (errors.isNotEmpty()) {
            Result.failure(AppFailure.ValidationError(errors))
        } else {
            Result.success(nameValidation.getOrNull()!!.trim())
        }
    }
}

data class UpdateWorldNameStep(val worldId: Int) : Step<String, AppFailure.DatabaseError, String> {
    override suspend fun process(input: String): Result<AppFailure.DatabaseError, String> {
        return DatabaseSteps.update<String>(
            sql = SafeSQL.update("""
                UPDATE world 
                SET name = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            """),
            parameterSetter = { statement, name ->
                statement.setString(1, name)
                statement.setInt(2, worldId)
            }
        ).process(input).map { input }
    }
}
