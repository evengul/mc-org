package app.mcorg.pipeline.resources

import app.mcorg.domain.pipeline.Step
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.resources.commonsteps.GetProgressForProjectStep
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByItemInput
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByItemStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.dsl.pages.overallProgressInner
import app.mcorg.presentation.templated.dsl.pages.planActivityCount
import app.mcorg.presentation.templated.dsl.pages.planProgressTotals
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.span
import kotlinx.html.stream.createHTML

/**
 * Parsed input for a plan-activity progress update, identified by (projectId, itemId).
 *
 * @param projectId owner project.
 * @param itemId Minecraft item id (e.g. "minecraft:iron_ingot").
 * @param delta positive or negative counter delta (non-zero validated).
 * @param required the plan's quantity ceiling for clamping.
 */
data class PlanProgressInput(
    val projectId: Int,
    val itemId: String,
    val delta: Int,
    val required: Long,
)

/**
 * PATCH /worlds/{worldId}/projects/{projectId}/plan/progress
 *
 * Updates progress for a plan activity identified by (projectId, itemId).
 * Does NOT require a resource_gathering row — engine-derived activities have none.
 *
 * Form params:
 *   itemId   — Minecraft item id
 *   amount   — delta (non-zero)
 *   required — plan quantity ceiling
 *
 * Responds with:
 *   - Updated activity row (outerHTML swap of #plan-activity-{itemSlug})
 *   - OOB update of #overall-progress (bar + label) showing the project-wide gathered total
 */
suspend fun ApplicationCall.handleUpdatePlanProgress() {
    val params = receiveParameters()
    val worldId = getWorldId()
    val projectId = getProjectId()

    handlePipeline(
        onSuccess = { result: PlanProgressResult ->
            respondHtml(buildPlanProgressResponse(worldId, projectId, result))
        }
    ) {
        val input = ValidatePlanProgressInputStep.run(params).let {
            PlanProgressInput(projectId, it.itemId, it.delta, it.required)
        }
        UpsertProgressByItemStep.run(UpsertProgressByItemInput(input.projectId, input.itemId, input.delta, input.required))

        // Reload full project progress map after upsert — covers derived items too
        val progressMap = GetProgressForProjectStep.run(projectId)
        val collected = progressMap[input.itemId] ?: 0

        // Re-derive the plan to compute project-wide totals for the OOB header.
        // Failure is non-fatal: null plan means we skip the OOB header update.
        val plan: GatheringPlan? = when (val r = GenerateGatheringPlanStep.process(GatheringPlanInput(projectId, worldId))) {
            is Result.Success -> r.value
            is Result.Failure -> null
        }

        // Project-wide totals for the overall-progress OOB update
        val overallTotals: Pair<Long, Long>? = plan?.let { planProgressTotals(it, progressMap) }

        PlanProgressResult(
            itemId = input.itemId,
            itemName = input.itemId.substringAfterLast(':').replace('_', ' ')
                .replaceFirstChar { it.uppercaseChar() },
            collected = collected.toLong(),
            required = input.required,
            sourceLabel = null, // source label not available without plan re-derive here
            overallTotals = overallTotals,
        )
    }
}

data class PlanProgressResult(
    val itemId: String,
    val itemName: String,
    val collected: Long,
    val required: Long,
    val sourceLabel: String?,
    /** Project-wide (totalRequired, totalCollected) across countable activities; null if plan unavailable. */
    val overallTotals: Pair<Long, Long>? = null,
)

private fun buildPlanProgressResponse(worldId: Int, projectId: Int, result: PlanProgressResult): String {
    val itemSlug = result.itemId.replace(":", "-")
    val rowId = "plan-activity-$itemSlug"
    val percent = if (result.required > 0) (result.collected.coerceAtMost(result.required) * 100 / result.required).toInt() else 0
    val complete = result.required > 0 && result.collected >= result.required

    val rowHtml = createHTML().div("resource-row${if (complete) " resource-row--complete" else ""}") {
        id = rowId
        attributes["data-item-name"] = result.itemName
        attributes["data-progress-pct"] = percent.toString()
        attributes["data-required"] = result.required.toString()

        div("resource-row__desktop") {
            div("resource-row__name${if (complete) " resource-row__name--complete" else ""}") {
                +result.itemName
            }

            div("resource-row__progress") {
                div("progress") {
                    div("progress__fill${if (complete) " progress__fill--complete" else ""}") {
                        attributes["style"] = "width: ${percent}%"
                        attributes["role"] = "progressbar"
                        attributes["aria-valuenow"] = result.collected.toString()
                        attributes["aria-valuemin"] = "0"
                        attributes["aria-valuemax"] = result.required.toString()
                    }
                }
            }

            planActivityCount(result.itemId, result.itemName, itemSlug, result.collected, result.required, complete)

            if (result.sourceLabel != null) {
                span("resource-row__source") { +result.sourceLabel }
            }

            if (!complete) {
                div("resource-row__counters") {
                    intArrayOf(-1728, -64, -1, 1, 64, 1728).forEach { amount ->
                        button(classes = "btn btn--ghost btn--sm resource-row__counter-btn") {
                            attributes["hx-patch"] = "/worlds/$worldId/projects/$projectId/plan/progress"
                            attributes["hx-vals"] =
                                """{"itemId": "${result.itemId}", "amount": $amount, "required": ${result.required}}"""
                            attributes["hx-target"] = "#$rowId"
                            attributes["hx-swap"] = "outerHTML"
                            +if (amount > 0) "+$amount" else "$amount"
                        }
                    }
                }
            }
        }
    }

    // OOB update for #overall-progress — project-wide totals from plan re-derive.
    // Emitted only when totals are available; if plan derivation failed, header is not updated.
    // Mirrors the page render (label + bar) so the "N% gathered · M to go" label stays fresh.
    val oobHtml = result.overallTotals?.let { (totalRequired, totalCollected) ->
        createHTML().div {
            id = "overall-progress"
            hxOutOfBands("outerHTML:#overall-progress")
            if (totalRequired > 0) {
                overallProgressInner(totalRequired, totalCollected)
            }
        }
    } ?: ""

    return rowHtml + oobHtml
}

/** Parsed and validated params for a plan progress update. */
private data class ValidatedPlanProgressParams(
    val itemId: String,
    val delta: Int,
    val required: Long,
)

private object ValidatePlanProgressInputStep : Step<Parameters, AppFailure.ValidationError, ValidatedPlanProgressParams> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, ValidatedPlanProgressParams> {
        val itemId = input["itemId"]
        if (itemId.isNullOrBlank()) {
            return Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.MissingParameter("itemId")))
            )
        }

        val amountStr = input["amount"]
        val amount = amountStr?.toIntOrNull()
        if (amountStr.isNullOrBlank() || amount == null) {
            return Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.MissingParameter("amount")))
            )
        }
        if (amount == 0) {
            return Result.failure(
                AppFailure.ValidationError(
                    listOf(ValidationFailure.InvalidValue("amount", listOf("any non-zero integer")))
                )
            )
        }

        val requiredStr = input["required"]
        if (requiredStr.isNullOrBlank()) {
            return Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.MissingParameter("required")))
            )
        }
        val required = requiredStr.toLongOrNull()
        if (required == null || required <= 0) {
            return Result.failure(
                AppFailure.ValidationError(
                    listOf(ValidationFailure.InvalidValue("required", listOf("a positive integer")))
                )
            )
        }

        return Result.success(ValidatedPlanProgressParams(itemId, amount, required))
    }
}
