package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.minecraft.MinecraftLocation
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.locationDetails
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import kotlinx.html.div
import kotlinx.html.stream.createHTML

sealed interface EditLocationFailure {
    data class ValidationFailure(val errors: List<app.mcorg.pipeline.failure.ValidationFailure>) : EditLocationFailure
    object DatabaseFailure : EditLocationFailure
}

suspend fun ApplicationCall.handleEditLocation() {
    val projectId = this.getProjectId()
    val parameters = this.receiveParameters()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().div {
                locationDetails(it)
            } + createHTML().div {
                hxOutOfBands("innerHTML:.project-location-chip")
                + "${it.x}, ${it.y}, ${it.z}"
            })
        },
        onFailure = { respond(HttpStatusCode.InternalServerError, "An error occurred while updating the project location") }
    ) {
        step(Step.value(parameters))
            .step(ValidateLocationStep)
            .step(UpdateLocationStep(projectId))
    }
}

object ValidateLocationStep : Step<Parameters, EditLocationFailure.ValidationFailure, MinecraftLocation> {
    override suspend fun process(input: Parameters): Result<EditLocationFailure.ValidationFailure, MinecraftLocation> {
        val x = ValidationSteps.requiredInt("x") { it }.process(input)
        val y = ValidationSteps.requiredInt("y") { it }.process(input)
        val z = ValidationSteps.requiredInt("z") { it }.process(input)
        val dimension = ValidationSteps.required("dimension") { it }
            .process(input)
            .flatMap { dim -> ValidationSteps.validateCustom<ValidationFailure, String>("dimension", "Unknown Dimension, must be one of [${Dimension.entries}]", { e -> e }) {
                Dimension.entries.firstOrNull { dim -> dim.name == it.uppercase() }
                    ?: return@validateCustom false
                true
            }.process(dim) }
            .flatMap { dim -> Result.success(Dimension.valueOf(dim)) }

        val errors = buildList {
            if (x is Result.Failure) add(x.error)
            if (y is Result.Failure) add(y.error)
            if (z is Result.Failure) add(z.error)
            if (dimension is Result.Failure) add(dimension.error)
        }

        return if (errors.isNotEmpty()) {
            Result.failure(EditLocationFailure.ValidationFailure(errors))
        } else {
            Result.success(
                MinecraftLocation(
                    x = (x as Result.Success).value,
                    y = (y as Result.Success).value,
                    z = (z as Result.Success).value,
                    dimension = (dimension as Result.Success).value
                )
            )
        }
    }
}

data class UpdateLocationStep(val projectId: Int) : Step<MinecraftLocation, EditLocationFailure.DatabaseFailure, MinecraftLocation> {
    override suspend fun process(input: MinecraftLocation): Result<EditLocationFailure.DatabaseFailure, MinecraftLocation> {
        val result = DatabaseSteps.update<MinecraftLocation, EditLocationFailure.DatabaseFailure>(
            SafeSQL.update("UPDATE projects SET location_x = ?, location_y = ?, location_z = ?, location_dimension = ? WHERE id = ?"),
            { statement, loc ->
                statement.setInt(1, loc.x)
                statement.setInt(2, loc.y)
                statement.setInt(3, loc.z)
                statement.setString(4, loc.dimension.name)
                statement.setInt(5, projectId)
            },
            { EditLocationFailure.DatabaseFailure }
        ).process(input)

        return when(result) {
            is Result.Success -> Result.success(input)
            is Result.Failure -> Result.failure(result.error)
        }
    }
}