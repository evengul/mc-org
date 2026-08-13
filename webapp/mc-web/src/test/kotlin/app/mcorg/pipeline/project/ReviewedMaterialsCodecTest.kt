package app.mcorg.pipeline.project

import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * MCO-315 — the review list as one field, and the promise that it can never arrive short
 * without saying so.
 */
class ReviewedMaterialsCodecTest {

    private fun decode(raw: String?) = ReviewedMaterialsCodec.decode(raw)

    private fun succeed(raw: String?): List<ReviewedMaterial> {
        val result = decode(raw)
        assertIs<Result.Success<List<ReviewedMaterial>>>(result, "expected success, got $result")
        return result.value
    }

    private fun failureMessage(raw: String?): String {
        val result = decode(raw)
        assertIs<Result.Failure<AppFailure>>(result, "expected failure, got $result")
        val error = result.error
        assertIs<AppFailure.ValidationError>(error)
        return error.errors.filterIsInstance<ValidationFailure.CustomValidation>().joinToString(" ") { it.message }
    }

    @Test
    fun `a list round-trips with its order, quantities and exclusions intact`() {
        val rows = listOf(
            ReviewedMaterial("minecraft:stone", 1024, included = true),
            ReviewedMaterial("minecraft:shroomlight", 64, included = false),
            ReviewedMaterial("minecraft:oak_planks", 12, included = true),
        )

        assertEquals(rows, succeed(ReviewedMaterialsCodec.encode(rows)))
    }

    @Test
    fun `an empty list is a valid payload, not a missing one`() {
        val encoded = ReviewedMaterialsCodec.encode(emptyList())

        assertEquals("v1;0", encoded)
        assertTrue(succeed(encoded).isEmpty())
    }

    @Test
    fun `a list far larger than the old 1000-parameter cap survives whole`() {
        // The bug: two fields per row meant ~466 rows fit in a request body. One field per
        // list is flat in the number of rows, so 5000 costs the same one parameter as one.
        val rows = (1..5000).map { ReviewedMaterial("minecraft:bulk_$it", it, included = it % 7 != 0) }

        val decoded = succeed(ReviewedMaterialsCodec.encode(rows))

        assertEquals(rows, decoded)
        assertEquals(rows.count { it.included }, decoded.count { it.included })
    }

    @Test
    fun `ids carrying every character a resource location allows survive`() {
        val rows = listOf(
            ReviewedMaterial("minecraft:block/oak_log-2.0", 3, included = true),
            ReviewedMaterial("some_mod:deep.slate_thing", 7, included = false),
        )

        assertEquals(rows, succeed(ReviewedMaterialsCodec.encode(rows)))
    }

    @Test
    fun `the exact shape the browser script writes is accepted`() {
        // `import-review.js` rebuilds this field from the checkboxes, so the two encoders sit
        // on opposite sides of a wire format with no schema between them. This literal is the
        // format, spelled out independently of [ReviewedMaterialsCodec.encode].
        val fromBrowser = "v1;3;minecraft:stone=1024;!minecraft:shroomlight=64;minecraft:oak_planks=12"

        assertEquals(
            listOf(
                ReviewedMaterial("minecraft:stone", 1024, included = true),
                ReviewedMaterial("minecraft:shroomlight", 64, included = false),
                ReviewedMaterial("minecraft:oak_planks", 12, included = true),
            ),
            succeed(fromBrowser),
        )
        assertEquals(
            fromBrowser,
            ReviewedMaterialsCodec.encode(succeed(fromBrowser)),
            "and the server writes exactly what the browser writes",
        )
    }

    // ---- loud failures ---------------------------------------------------------------

    @Test
    fun `a payload cut short is refused rather than silently accepted`() {
        // Exactly the shape truncation takes: the header still claims the full list, the
        // rows behind it stop early. This is the check that makes MCO-315 unrepeatable.
        val full = ReviewedMaterialsCodec.encode(
            (1..600).map { ReviewedMaterial("minecraft:bulk_$it", it, included = true) }
        )
        val truncated = full.split(";").take(2 + 466).joinToString(";")

        val message = failureMessage(truncated)

        assertTrue(message.contains("incomplete"), "expected a truncation error, got: $message")
        assertTrue(message.contains("466 of 600"), "the error should name what was lost, got: $message")
    }

    @Test
    fun `a payload carrying more rows than it declares is refused`() {
        val message = failureMessage("v1;1;minecraft:stone=1;minecraft:oak_planks=2")

        assertTrue(message.contains("incomplete"), message)
    }

    @Test
    fun `a missing field is refused rather than treated as an empty list`() {
        val message = failureMessage(null)

        assertTrue(message.contains("did not arrive"), message)
    }

    @Test
    fun `a blank field is refused`() {
        assertIs<Result.Failure<AppFailure>>(decode("   "))
    }

    @Test
    fun `an unrecognised version marker is refused`() {
        val message = failureMessage("v2;1;minecraft:stone=1")

        assertTrue(message.contains("does not recognise"), message)
    }

    @Test
    fun `a payload without a row count is refused`() {
        assertIs<Result.Failure<AppFailure>>(decode("v1"))
        assertIs<Result.Failure<AppFailure>>(decode("v1;many;minecraft:stone=1"))
        assertIs<Result.Failure<AppFailure>>(decode("v1;-1"))
    }

    @Test
    fun `a list beyond the sanity bound is refused before anything is built`() {
        val message = failureMessage("v1;${ReviewedMaterialsCodec.MAX_ROWS + 1}")

        assertTrue(message.contains("more than the"), message)
    }

    @Test
    fun `a row with no quantity is refused`() {
        assertIs<Result.Failure<AppFailure>>(decode("v1;1;minecraft:stone"))
        assertIs<Result.Failure<AppFailure>>(decode("v1;1;=64"))
    }

    @Test
    fun `a non-positive or unreadable quantity is refused`() {
        assertIs<Result.Failure<AppFailure>>(decode("v1;1;minecraft:stone=0"))
        assertIs<Result.Failure<AppFailure>>(decode("v1;1;minecraft:stone=-4"))
        assertIs<Result.Failure<AppFailure>>(decode("v1;1;minecraft:stone=lots"))
    }
}
