package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
import app.mcorg.pipeline.resources.applySubstitution
import app.mcorg.pipeline.resources.findSubstitutionFamilies
import app.mcorg.pipeline.resources.substitutedId
import app.mcorg.pipeline.resources.variantTokens
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.importReviewMaterials
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters

/**
 * MCO-304: batch substitution on the import review screen — all oak to spruce, all blue
 * terracotta to red, before any of it is a project.
 *
 * Nothing is persisted. The review screen holds the whole material list in one form, so a
 * swap is a round-trip: post the form, rewrite the list, hand back the same section of the
 * page. That keeps substitution exactly as reversible as every other edit on this screen —
 * swap back, or hit Cancel — and leaves no draft state to garbage-collect.
 *
 * Substitution is demand-side: it edits *what you want*, and the scorer never sees it as
 * something to optimise. Pins and tag resolutions edit how to get it, and live elsewhere.
 */
suspend fun ApplicationCall.handleSubstituteInImportReview() {
    val worldId = this.getWorldId()
    val parameters = receiveParameters()
    val items = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    handlePipeline(
        onSuccess = { reviewed: ReviewedList ->
            respondHtml(
                importReviewMaterials(
                    worldId = worldId,
                    requirements = reviewed.requirements,
                    excluded = reviewed.excluded,
                    families = findSubstitutionFamilies(
                        catalog = items,
                        requirements = reviewed.requirements.map { it.key.id },
                    ),
                    // Recomputed, not carried over: a swap changes which rows are painful.
                    // Trading oak for crimson can retire an unobtainable row or introduce one,
                    // and a stale strip would describe the list the user just replaced.
                    warnings = computeImportWarnings(worldId, reviewed.requirements),
                )
            )
        }
    ) {
        SubstituteReviewedListStep(items).run(parameters)
    }
}

/**
 * The review screen's list as it currently stands: every row, plus which of them the user
 * has struck. [excluded] is the complement of the checked boxes rather than a stored concept —
 * the screen has no server-side state and this keeps it that way.
 */
data class ReviewedList(
    val requirements: Map<Item, Int>,
    val excluded: Set<String>,
)

/**
 * Reads the review form, applies one family swap, and hands back the rewritten list.
 *
 * The form sends the whole list — struck rows included — in one `materials` field (MCO-315).
 * Carrying the struck rows is what lets an excluded row survive a swap; from the kept rows
 * alone, striking a row and then swapping would delete it for good. Before MCO-315 that
 * guarantee was a second field per row, which is exactly what overflowed the request's
 * parameter cap and made the tail of a large list vanish across a swap.
 *
 * Ids are re-checked against the world catalog. The review page is a form like any other and
 * what it sends is not trusted just because the server rendered it a moment ago.
 */
internal data class SubstituteReviewedListStep(val availableItems: List<Item>) :
    Step<Parameters, AppFailure, ReviewedList> {

    override suspend fun process(input: Parameters): Result<AppFailure, ReviewedList> {
        val fromToken = input["from"]?.takeIf { it.isNotBlank() }
            ?: return Result.failure(AppFailure.customValidationError("from", "Nothing to swap"))
        val toToken = input["to"]?.takeIf { it.isNotBlank() }
            ?: return Result.failure(AppFailure.customValidationError("to", "Pick what to swap to"))

        val rows = when (val decoded = ReviewedMaterialsCodec.decode(input[ReviewedMaterialsCodec.FIELD])) {
            is Result.Failure -> return Result.Failure(decoded.error)
            is Result.Success -> decoded.value
        }

        val byId = availableItems.associateBy { it.id }
        val requirements = LinkedHashMap<Item, Int>()
        val checked = mutableSetOf<String>()

        rows.forEach { row ->
            val item = byId[row.itemId]
                ?: return Result.failure(
                    AppFailure.customValidationError("materials", "Unknown item: ${row.itemId}")
                )
            requirements[item] = row.amount
            if (row.included) checked.add(row.itemId)
        }

        if (requirements.isEmpty()) {
            return Result.failure(
                AppFailure.customValidationError("materials", "There is nothing here to swap")
            )
        }

        val tokens = variantTokens(availableItems)
        val catalogIds = availableItems.mapTo(HashSet()) { it.id }
        val swapped = applySubstitution(availableItems, requirements, fromToken, toToken, tokens)

        // Exclusions follow their row through the swap: strike the oak stairs, swap oak to
        // spruce, and it is the spruce stairs that stay struck.
        //
        // Where a kept row and a struck row land on the same id, the merged row is kept. The
        // alternative — striking a row the user had checked, or dropping the struck row's
        // quantity — either surprises them or loses a number they can never get back; a row
        // that comes back checked is visible in the total and one click from struck again.
        val keptTargets = requirements.keys
            .filter { it.id in checked }
            .mapTo(mutableSetOf()) { substitutedId(catalogIds, it.id, fromToken, toToken, tokens) }
        val excluded = swapped.keys.mapNotNullTo(mutableSetOf()) { it.id.takeIf { id -> id !in keptTargets } }

        return Result.success(ReviewedList(swapped, excluded))
    }
}
