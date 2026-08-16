package app.mcorg.pipeline.world.settings.general

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.AlertType
import app.mcorg.presentation.templated.dsl.createAlert
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.li
import kotlinx.html.stream.createHTML

/** Same ceiling as the input's `max` — a threshold nothing can reach is a disabled feature. */
private const val MAX_FARM_SCALE_THRESHOLD = 10_000_000

/**
 * Updates a world's farm-scale threshold (MCO-401) — the raw-gather quantity at or above which
 * a plan marks a material as worth building a farm for.
 */
suspend fun ApplicationCall.handleUpdateFarmScaleThreshold() {
    val parameters = receiveParameters()
    val worldId = getWorldId()

    handlePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                createAlert(
                    id = "farm-scale-threshold-updated-success-alert",
                    type = AlertType.SUCCESS,
                    title = "Farm-scale threshold updated",
                    autoClose = true
                )
            })
        },
    ) {
        val threshold = ValidateFarmScaleThresholdStep.run(parameters)
        UpdateFarmScaleThresholdStep(worldId).run(threshold)
    }
}

/**
 * A whole number of items, at least 1.
 *
 * Zero or negative would mark *every* raw material farm-scale, which conveys exactly as much
 * as marking none — so it is rejected here as well as by the column's CHECK constraint, to say
 * why rather than fail on write.
 */
object ValidateFarmScaleThresholdStep : Step<Parameters, AppFailure.ValidationError, Int> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Int> {
        val raw = input["farmScaleThreshold"]?.trim()

        val parsed = raw?.toIntOrNull()
        val failure = when {
            raw.isNullOrEmpty() -> "A threshold is required"
            parsed == null -> "The threshold must be a whole number"
            parsed < 1 -> "The threshold must be at least 1 — zero would mark everything"
            parsed > MAX_FARM_SCALE_THRESHOLD -> "The threshold must be at most ${"%,d".format(MAX_FARM_SCALE_THRESHOLD)}"
            else -> null
        }

        return if (failure != null) {
            Result.failure(
                AppFailure.ValidationError(
                    listOf(ValidationFailure.CustomValidation("farmScaleThreshold", failure))
                )
            )
        } else {
            Result.success(parsed!!)
        }
    }
}

data class UpdateFarmScaleThresholdStep(val worldId: Int) : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.update("""
                UPDATE world
                SET farm_scale_threshold = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            """),
            parameterSetter = { statement, threshold ->
                statement.setInt(1, threshold)
                statement.setInt(2, worldId)
            }
        ).process(input).map { input }
    }
}
