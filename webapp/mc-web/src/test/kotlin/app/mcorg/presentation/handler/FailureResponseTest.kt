package app.mcorg.presentation.handler

import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * MCO-553 — the one exhaustive test over `AppFailure`: log volume, HTTP status and response
 * shape per variant, from the single table in `ErrorHandler.kt`.
 *
 * Two claims. First, every leaf of the sealed hierarchy has a row here — found by reflection, so
 * adding a variant to `AppFailure` fails this test until someone decides what the boundary does
 * with it. Second, each row says what it says. The `when` in `toFailureResponse` is exhaustive
 * by the compiler; this pins that the *values* were chosen, not merely that a branch exists.
 */
class FailureResponseTest {

    private data class Row(
        val failure: AppFailure,
        val volume: FailureVolume,
        val status: HttpStatusCode,
        val shape: (FailureResponse) -> Unit,
    )

    private fun alert(id: String): (FailureResponse) -> Unit = { r ->
        assertIs<FailureResponse.Alert>(r)
        assertEquals(id, r.id)
    }

    private fun redirectTo(url: String): (FailureResponse) -> Unit = { r ->
        assertIs<FailureResponse.RedirectTo>(r)
        assertEquals(url, r.url)
    }

    private val validation: (FailureResponse) -> Unit = { r -> assertIs<FailureResponse.ValidationMessages>(r) }

    private val generic = alert("generic-error")
    private val e = FailureVolume.ERROR
    private val i500 = HttpStatusCode.InternalServerError

    private val rows = listOf(
        // AuthError
        Row(AppFailure.AuthError.NotAuthorized, FailureVolume.WARN, HttpStatusCode.Forbidden, alert("not-authorized-error")),
        Row(AppFailure.AuthError.MissingToken, FailureVolume.SILENT, HttpStatusCode.Found, redirectTo("/auth/sign-in?redirect_to=/worlds/3?tab=x")),
        Row(AppFailure.AuthError.CouldNotCreateToken, e, i500, alert("token-creation-error")),
        Row(AppFailure.AuthError.ConvertTokenError.expiredToken(), FailureVolume.INFO, HttpStatusCode.Found, redirectTo("/auth/sign-out?error=expired_token")),
        // DatabaseError
        Row(AppFailure.DatabaseError.ConnectionError, e, i500, generic),
        Row(AppFailure.DatabaseError.StatementError, e, i500, generic),
        Row(AppFailure.DatabaseError.IntegrityConstraintError, e, i500, generic),
        Row(AppFailure.DatabaseError.ResultMappingError, e, i500, generic),
        Row(AppFailure.DatabaseError.UnknownError, e, i500, generic),
        Row(AppFailure.DatabaseError.NoIdReturned, e, i500, generic),
        Row(AppFailure.DatabaseError.NotFound, FailureVolume.INFO, HttpStatusCode.NotFound, alert("not-found-error")),
        // ApiError
        Row(AppFailure.ApiError.NetworkError, e, i500, generic),
        Row(AppFailure.ApiError.TimeoutError, e, i500, generic),
        Row(AppFailure.ApiError.RateLimitExceeded, e, i500, generic),
        Row(AppFailure.ApiError.HttpError(503, "upstream said no"), e, i500, generic),
        Row(AppFailure.ApiError.SerializationError, e, i500, generic),
        Row(AppFailure.ApiError.ChecksumMismatch("abc", "def"), e, i500, generic),
        Row(AppFailure.ApiError.UnknownError, e, i500, generic),
        // Top-level
        Row(AppFailure.customValidationError("name", "Name is required"), FailureVolume.SILENT, HttpStatusCode.UnprocessableEntity, validation),
        Row(AppFailure.ValidationError(listOf(ValidationFailure.MissingParameter("name"))), FailureVolume.SILENT, HttpStatusCode.BadRequest, validation),
        Row(AppFailure.Redirect("/worlds", mapOf("q" to "a b")), FailureVolume.SILENT, HttpStatusCode.Found, redirectTo("/worlds?q=a+b")),
        Row(AppFailure.IllegalConfigurationError("no key"), e, i500, generic),
        Row(AppFailure.FileError(Step::class.java as Class<Step<*, *, *>>, "x.jar"), e, i500, generic),
    )

    @Test
    fun `every AppFailure variant has a row`() {
        val leaves = leavesOf(AppFailure::class)
        val covered = rows.map { it.failure::class }.toSet()

        val missing = leaves - covered
        assertTrue(missing.isEmpty(), "AppFailure variants with no row in this test: ${missing.map { it.simpleName }}")
        val stale = covered - leaves
        assertTrue(stale.isEmpty(), "rows for types that are not AppFailure leaves: ${stale.map { it.simpleName }}")
    }

    @Test
    fun `each variant maps to the stated volume, status and response`() {
        rows.forEach { row ->
            val response = row.failure.toFailureResponse(requestUri = "/worlds/3?tab=x", reference = null)
            assertEquals(row.volume, response.volume, "volume of ${row.failure}")
            assertEquals(row.status, response.status, "status of ${row.failure}")
            row.shape(response)
        }
    }

    @Test
    fun `the generic alert quotes the call id and the specific ones do not`() {
        val generic = AppFailure.DatabaseError.ConnectionError.toFailureResponse("/x", reference = "req-42")
        assertIs<FailureResponse.Alert>(generic)
        assertTrue("(reference: req-42)" in generic.message, generic.message)

        val notFound = AppFailure.DatabaseError.NotFound.toFailureResponse("/x", reference = "req-42")
        assertIs<FailureResponse.Alert>(notFound)
        assertTrue("req-42" !in notFound.message, notFound.message)

        val noId = AppFailure.DatabaseError.ConnectionError.toFailureResponse("/x", reference = null)
        assertIs<FailureResponse.Alert>(noId)
        assertTrue("reference" !in noId.message, noId.message)
    }

    @Test
    fun `mixed validation failures answer 400, a uniform set keeps its own status`() {
        val mixed = AppFailure.ValidationError(
            listOf(ValidationFailure.MissingParameter("a"), ValidationFailure.InvalidFormat("b"))
        ).toFailureResponse("/x", null)
        assertEquals(HttpStatusCode.BadRequest, mixed.status)

        val uniform = AppFailure.ValidationError(
            listOf(ValidationFailure.OutOfRange("a", 1, 2), ValidationFailure.InvalidLength("b", 1, 2))
        ).toFailureResponse("/x", null)
        assertEquals(HttpStatusCode.UnprocessableEntity, uniform.status)
    }

    private fun leavesOf(k: KClass<out AppFailure>): Set<KClass<out AppFailure>> =
        if (k.isSealed) k.sealedSubclasses.flatMap { leavesOf(it) }.toSet() else setOf(k)
}
