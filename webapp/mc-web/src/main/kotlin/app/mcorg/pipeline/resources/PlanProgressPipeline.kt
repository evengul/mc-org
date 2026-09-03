package app.mcorg.pipeline.resources

import app.mcorg.domain.model.world.World
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
import app.mcorg.presentation.templated.dsl.pages.feedsLine
import app.mcorg.presentation.templated.dsl.pages.lootTableName
import app.mcorg.presentation.templated.dsl.pages.overallProgressInner
import app.mcorg.presentation.templated.dsl.pages.planActivityCount
import app.mcorg.presentation.templated.dsl.pages.workRowCollapsedHtml
import app.mcorg.presentation.templated.dsl.pages.workRowStateOf
import app.mcorg.presentation.templated.dsl.pages.workRowStripHtml
import app.mcorg.presentation.templated.dsl.pages.completedActivity
import app.mcorg.presentation.templated.dsl.pages.smallJobChipHtml
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

        // Re-derived plan lets us keep the "Smelting · 1 Raw Iron" source/ingredient label on
        // the swapped row, matching the initial render.
        val node = plan?.nodes?.get(input.itemId)
        val sourceLabel = node?.let {
            val detail = plan.let(::buildNodeIngredients)[input.itemId] ?: it.source?.let(::lootTableName)
            listOfNotNull(it.source?.getMethodLabel(), detail)
                .joinToString(" · ")
                .ifEmpty { null }
        }

        // Keep the "feeds …" reverse-provenance line on the swapped row, matching the initial render.
        val feedsLabel = plan?.let { buildFeedsLabels(it)[input.itemId] }

        // Which form the row was in when it was pressed, so it swaps back into the same one
        // rather than collapsing under the user mid-edit.
        val working = params["working"]?.toBooleanStrictOrNull() ?: false
        val chip = params["chip"]?.toBooleanStrictOrNull() ?: false
        val activity = plan?.activityList?.firstOrNull { it.item.id == input.itemId }
        // Only looked up when the line has left the plan, which is the only time it is needed.
        val catalogName = if (activity != null) null else {
            when (val r = GetItemNameStep.process(ItemNameInput(worldId, input.itemId))) {
                is Result.Success -> r.value
                is Result.Failure -> null
            }
        }
        val farmScaleThreshold = when (val r = GetFarmScaleThresholdStep.process(worldId)) {
            is Result.Success -> r.value
            is Result.Failure -> World.DEFAULT_FARM_SCALE_THRESHOLD
        }
        // Same rule as the page, dismissals included (MCO-407) — the badge must not reappear
        // because a counter was pressed.
        val isFarmScale = plan != null &&
            FarmScaleDemands.of(plan, farmScaleThreshold, farmDismissalsFor(worldId).itemIds())
                .any { it.itemId == input.itemId }

        PlanProgressResult(
            itemId = input.itemId,
            // The plan's name first; then the catalog, for a line that has just finished and so
            // left the plan; the id only if both are unavailable.
            itemName = activity?.item?.name
                ?: catalogName
                ?: input.itemId.substringAfterLast(':').replace('_', ' ')
                    .replaceFirstChar { it.uppercaseChar() },
            collected = collected.toLong(),
            required = input.required,
            sourceLabel = sourceLabel,
            activity = activity,
            working = working,
            chip = chip,
            isFarmScale = isFarmScale,
            feedsLabel = feedsLabel,
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
    /**
     * The plan node this row is, when the plan re-derived. Carried so the swapped row is built by
     * the same code as the first render — the old response rebuilt a lookalike from the item id,
     * which is why a row's name lost its "(Block)" the moment you pressed a counter.
     */
    val activity: app.mcorg.engine.plan.Activity? = null,
    /** Whether the caller was in the expanded work strip, so it swaps back into the same form. */
    val working: Boolean = false,
    /** Whether the caller was a small-jobs chip, which swaps back as a chip. */
    val chip: Boolean = false,
    val isFarmScale: Boolean = false,
    /** "Feeds 24 Birch Door · 40 Chest" reverse-provenance line; null when this feeds nothing. */
    val feedsLabel: FeedsLabel? = null,
    /** Project-wide (totalRequired, totalCollected) across countable activities; null if plan unavailable. */
    val overallTotals: Pair<Long, Long>? = null,
)

/**
 * The swapped row plus the out-of-band header update.
 *
 * The row is rendered by the same three functions the page uses, in whichever form the caller was
 * in — a chip stays a chip, a work strip stays a work strip, everything else is a collapsed line.
 * This used to be a fourth, hand-rolled copy of the row markup, which drifted: it rebuilt the name
 * from the item id, so pressing a counter silently renamed "Oak Log (Block)" to "Oak Log".
 */
private fun buildPlanProgressResponse(worldId: Int, projectId: Int, result: PlanProgressResult): String {
    // A finished line is no longer in the plan, so it is rebuilt rather than dropped — rendering
    // nothing here deleted the row out from under whoever had just ticked it.
    val activity = result.activity
        ?: completedActivity(result.itemId, result.itemName, result.required)

    val rowHtml = activity.let { activity ->
        val state = workRowStateOf(
            activity = activity,
            progress = mapOf(activity.item.id to result.collected.toInt()),
            feedsLabels = result.feedsLabel?.let { mapOf(activity.item.id to it) } ?: emptyMap(),
            farmScaleIds = if (result.isFarmScale) setOf(activity.item.id) else emptySet(),
        ).copy(sourceLabel = result.sourceLabel)

        when {
            result.chip -> smallJobChipHtml(worldId, projectId, state)
            result.working -> workRowStripHtml(worldId, projectId, state)
            else -> workRowCollapsedHtml(worldId, projectId, state)
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
