package app.mcorg.pipeline.project

import app.mcorg.presentation.templated.dsl.Link

/**
 * The wizard's place in a batch import of suggested designs (MCO-459).
 *
 * MCO-294 renders a plan's farm-scale demand grouped by the design that covers it — on the
 * dogfood world, seven designs against ten lines. Importing them was seven independent
 * decisions: click Import, review, create, land on the *new* project, navigate back to the
 * plan you were reading. Every individual step was right (MCO-306 put review there
 * deliberately, MCO-457's redirect is correct for a single import); what was wrong is that a
 * roadmap was being walked as if it were unrelated errands.
 *
 * ## Why the queue lives in the URL
 *
 * There is no server-side wizard state, no session key, no draft rows. The whole queue is two
 * query parameters, so a review step is still exactly what [handleReviewIdeaImport] says it
 * is — "a GET … reloadable and shareable rather than tied to one upload". Reloading step 2 of
 * 3 lands on step 2 of 3. Closing the tab loses nothing that was not already created.
 *
 * ## Why each step creates instead of accumulating
 *
 * The alternative — collect every reviewed list and commit at the end — means carrying
 * hundreds of material rows across N requests, and one validation failure at the end losing
 * all of it. Creating as you go keeps each step atomic and exactly today's POST, and bailing
 * out at 2 of 3 leaves two farms you did want. It is also self-healing: the plan you return to
 * re-renders, and MCO-458/MCO-461's `alreadyCovered` means the farms you just made no longer
 * appear as suggestions, so the third is still offered and nothing is double-imported.
 */
data class ImportQueue(
    /** Every design selected on the plan, in the order the suggestion list showed them. */
    val ideaIds: List<Int>,
    /** The project whose plan the batch started from — where "Done" and the last step land. */
    val returnToProjectId: Int,
) {
    val size: Int get() = ideaIds.size

    /** 1-based position of [ideaId] for "Review N of M", or null when it is not in the queue. */
    fun positionOf(ideaId: Int): Int? = ideaIds.indexOf(ideaId).takeIf { it >= 0 }?.plus(1)

    /** The next design to review after [ideaId], or null when this is the last one. */
    fun nextAfter(ideaId: Int): Int? {
        val index = ideaIds.indexOf(ideaId)
        if (index < 0) return null
        return ideaIds.getOrNull(index + 1)
    }

    /** Where the wizard lands once there is nothing left to review. */
    fun returnHref(worldId: Int): String =
        Link.Worlds.world(worldId).project(returnToProjectId).to

    /** The review URL for [ideaId], carrying the queue forward unchanged. */
    fun reviewHref(ideaId: Int, worldId: Int): String =
        Link.Ideas.single(ideaId) + "/import/review?worldId=$worldId&" + queryString()

    /** The two parameters that carry this queue, for a link or a hidden field pair. */
    fun queryString(): String = "$QUEUE_PARAM=${ideaIds.joinToString(",")}&$RETURN_PARAM=$returnToProjectId"

    /** The same two, as hidden form fields for the review page to post back. */
    fun hiddenFields(): Map<String, String> = mapOf(
        QUEUE_PARAM to ideaIds.joinToString(","),
        RETURN_PARAM to returnToProjectId.toString(),
    )

    companion object {
        const val QUEUE_PARAM = "queue"
        const val RETURN_PARAM = "returnTo"

        /**
         * Reads a queue from a request, or null when this is an ordinary single import.
         *
         * Null is the important case, not an error case: every existing door into the review
         * screen (the idea page, the world picker, a bookmark) sends neither parameter and
         * must keep behaving exactly as it did. A malformed or partial queue degrades to null
         * for the same reason — a batch is a convenience, and losing it costs one navigation,
         * where guessing at it could import into the wrong world.
         *
         * Duplicates are dropped rather than rejected. The plan cannot render one design twice,
         * so a repeat is a hand-edited URL; deduping keeps [positionOf] and [nextAfter] honest
         * (both key off the first index) instead of quietly looping.
         */
        fun from(rawQueue: String?, rawReturnTo: String?): ImportQueue? {
            val returnTo = rawReturnTo?.toIntOrNull() ?: return null
            val ids = rawQueue
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.distinct()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            return ImportQueue(ids, returnTo)
        }
    }
}

/**
 * One review step's place in the batch, as the page needs it (MCO-459).
 *
 * Built by the handler rather than by the template so the page stays a pure renderer, and so
 * the "not actually in the queue" case — a hand-edited URL naming a design the batch never
 * selected — is resolved server-side into a plain single import rather than a broken "Review
 * 0 of 3".
 */
data class ImportWizardStep(
    val position: Int,
    val total: Int,
    /** Review URL of the next design, or null on the last step. */
    val nextHref: String?,
    /** The plan this batch started from — "Done" now, and where the last create lands. */
    val returnHref: String,
) {
    val isLast: Boolean get() = nextHref == null
}
