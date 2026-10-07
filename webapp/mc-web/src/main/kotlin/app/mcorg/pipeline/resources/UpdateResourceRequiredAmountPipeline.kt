package app.mcorg.pipeline.resources

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters

suspend fun ApplicationCall.handleUpdateResourceRequiredAmount() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    // The demand changed, so the plan did: answer with all of it. Out of band, because the edit
    // comes from the table and from the open panel, and the panel must stay open — both callers
    // swap nothing themselves (MCO-585).
    handlePipeline(
        onSuccess = { respondHtml(listRerenderFragment(worldId, projectId, outOfBand = true) ?: return@handlePipeline) }
    ) {
        val input = ValidateRequiredAmountInputStep.run(parameters)
        UpdateRequiredAmountStep(resourceGatheringId).run(input)
    }
}

internal object ValidateRequiredAmountInputStep : Step<Parameters, AppFailure.ValidationError, Int> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Int> {
        val raw = input["required"]
        val value = raw?.toIntOrNull()
        return when {
            raw.isNullOrBlank() -> Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.MissingParameter("required")))
            )
            value == null -> Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.InvalidFormat("required", "Must be a number")))
            )
            value < 1 -> Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.CustomValidation("required", "Must be at least 1")))
            )
            else -> Result.success(value)
        }
    }
}

private data class UpdateRequiredAmountStep(val resourceGatheringId: Int) : Step<Int, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.update("UPDATE resource_gathering SET required = ? WHERE id = ?"),
            parameterSetter = { stmt, required ->
                stmt.setInt(1, required)
                stmt.setInt(2, resourceGatheringId)
            }
        ).process(input).map { }
    }
}
