package app.mcorg.pipeline.project

import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure

/**
 * One row of the import review list, as it travels between the browser and the server.
 *
 * [included] is the checkbox. An excluded row is still carried rather than dropped, so the
 * payload always describes the whole list the server rendered — which is what lets the
 * declared row count below tell a user's exclusions apart from a truncated transport.
 */
data class ReviewedMaterial(
    val itemId: String,
    val amount: Int,
    val included: Boolean,
)

/**
 * MCO-315: the review screen's material list travels as **one** form field.
 *
 * It used to be two fields per row — `qty[<id>]` for the checkbox and `row[<id>]` for the
 * always-submitted mirror. Ktor decodes an urlencoded body with `parseQueryString`, whose
 * `limit` defaults to **1000 pairs** and which simply *stops* at the limit rather than
 * failing, so a 560-material schematic lost everything past row ~466 without a word: 93 of
 * 558 materials silently missing from the created project.
 *
 * Raising the limit was rejected. The review screen deliberately holds the whole list in the
 * form rather than in a draft table (the MCO-303 decision), so the form *is* the storage and
 * it will keep growing; a bigger ceiling only moves the cliff. One field per list is flat in
 * the number of rows, which takes the cliff away instead.
 *
 * The payload is self-describing so that truncation can never be silent again: it declares
 * how many rows it carries, and [decode] refuses a payload whose rows do not add up. Any
 * transport that cuts the list short — a future parameter cap, a body limit, a proxy — now
 * produces a validation error the user can see instead of a project missing a fifth of its
 * materials.
 *
 * ```
 * materials := "v1" ";" count *( ";" row )
 * row       := [ "!" ] item-id "=" amount        ; "!" marks an excluded (unchecked) row
 * ```
 *
 * Minecraft resource locations are `[a-z0-9_.-]` plus `:` and `/`, so `;`, `=` and `!` can
 * never occur inside an id and need no escaping. The whole value is form-urlencoded by the
 * browser like any other field.
 */
object ReviewedMaterialsCodec {

    /** The single form field the whole list rides in. */
    const val FIELD = "materials"

    /**
     * A sanity bound, not a product limit — a vanilla catalog holds ~1500 distinct items, so
     * nothing a real import can produce comes close. It exists so a hand-rolled post cannot
     * ask the server to build an arbitrarily large list before validation gets a look in.
     */
    const val MAX_ROWS = 10_000

    private const val VERSION = "v1"
    private const val ROW_SEPARATOR = ";"
    private const val AMOUNT_SEPARATOR = '='
    private const val EXCLUDED_MARK = '!'

    fun encode(rows: List<ReviewedMaterial>): String = buildString {
        append(VERSION)
        append(ROW_SEPARATOR)
        append(rows.size)
        rows.forEach { row ->
            append(ROW_SEPARATOR)
            if (!row.included) append(EXCLUDED_MARK)
            append(row.itemId)
            append(AMOUNT_SEPARATOR)
            append(row.amount)
        }
    }

    /**
     * Reads the field back, refusing anything that is not exactly what was sent.
     *
     * Every failure here is loud on purpose. A missing field is the shape a *stale* review
     * page (one rendered before this change) submits, and answering it with "import what
     * happened to arrive" is how the original bug looked to the user.
     */
    fun decode(raw: String?): Result<AppFailure, List<ReviewedMaterial>> {
        val payload = raw?.trim()
        if (payload.isNullOrEmpty()) {
            return failure(
                "The material list did not arrive with the form. Reload the review page and try again."
            )
        }

        val parts = payload.split(ROW_SEPARATOR)
        if (parts.first() != VERSION) {
            return failure(
                "The material list is in a format Seam does not recognise. Reload the review page and try again."
            )
        }

        val declared = parts.getOrNull(1)?.toIntOrNull()
        if (declared == null || declared < 0) {
            return failure(
                "The material list did not say how many materials it carries. Reload the review page and try again."
            )
        }
        if (declared > MAX_ROWS) {
            return failure(
                "This import lists $declared distinct materials, more than the $MAX_ROWS Seam can take in one go."
            )
        }

        val encodedRows = parts.drop(2)
        if (encodedRows.size != declared) {
            return failure(
                "The material list arrived incomplete — ${encodedRows.size} of $declared materials reached the " +
                    "server. Nothing was imported. Reload the review page and try again."
            )
        }

        val rows = ArrayList<ReviewedMaterial>(encodedRows.size)
        for (encoded in encodedRows) {
            val included = !encoded.startsWith(EXCLUDED_MARK)
            val body = if (included) encoded else encoded.substring(1)
            val separator = body.lastIndexOf(AMOUNT_SEPARATOR)
            if (separator <= 0) {
                return failure(
                    "The material list contains a row Seam could not read. Reload the review page and try again."
                )
            }
            val itemId = body.substring(0, separator)
            val amount = body.substring(separator + 1).toIntOrNull()
            if (amount == null || amount <= 0) {
                return failure("$itemId needs a positive amount")
            }
            rows.add(ReviewedMaterial(itemId, amount, included))
        }

        return Result.success(rows)
    }

    private fun failure(message: String): Result<AppFailure, List<ReviewedMaterial>> =
        Result.failure(AppFailure.customValidationError(FIELD, message))
}
