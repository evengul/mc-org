package app.mcorg.presentation.handler

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.failure.userMessage
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.hxPartial
import app.mcorg.presentation.templated.dsl.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.dsl.AlertType
import app.mcorg.presentation.templated.dsl.createAlert
import app.mcorg.presentation.templated.dsl.fieldMessages
import app.mcorg.presentation.templated.error.errorPageLayout
import app.mcorg.presentation.templated.error.forbiddenPage
import app.mcorg.presentation.templated.error.notFoundPage
import app.mcorg.presentation.templated.error.serverErrorPage
import app.mcorg.presentation.utils.isHtmxRequest
import app.mcorg.presentation.utils.pageUri
import app.mcorg.presentation.utils.redirectClientOrBrowser
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.utils.signInRedirectUrl
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.AttributeKey
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.slf4j.LoggerFactory

private val errorLogger = LoggerFactory.getLogger("app.mcorg.presentation.ErrorBoundary")

/**
 * How loudly a failure reaching the boundary deserves to be reported.
 *
 * Most of what arrives here is ordinary: a signed-out visitor, a stale link, a form filled in
 * wrong. Logging all of it at ERROR would make the error log useless on the day it matters, which
 * is the failure mode this issue is trying to avoid — not replace with a noisier one.
 */
internal enum class FailureVolume { SILENT, INFO, WARN, ERROR }

/**
 * Everything the error boundary does with one [AppFailure]: how loudly to log it, what status to
 * answer with, and what to send.
 *
 * Until MCO-553 those were three separate exhaustive `when` tables over `AppFailure` in this file
 * — `volume()`, `defaultHandleError()` and `toHttpStatusCode()` — kept in step by hand. The
 * compiler made each one exhaustive on its own, but nothing tied them together, and the status
 * table had already drifted: it said `MissingToken` was a 401 while the response actually sent
 * was a 302 to the sign-in page. Now [toFailureResponse] is the one table, and
 * `FailureResponseTest` walks the sealed hierarchy so a new variant fails a test until it has a
 * row.
 *
 * [status] is the status of a plain (non-HTMX) response. A [RedirectTo] under HTMX is sent as a
 * 200 carrying `HX-Redirect`, since a browser follows a 302 inside the fetch and htmx would swap
 * the sign-in page into a fragment target.
 */
internal sealed interface FailureResponse {
    val volume: FailureVolume
    val status: HttpStatusCode

    /** An alert prepended to the page's alert container. */
    data class Alert(
        val id: String,
        val title: String,
        val message: String,
        override val status: HttpStatusCode,
        override val volume: FailureVolume,
    ) : FailureResponse

    /** A redirect: 302 for a plain request, `HX-Redirect` for an HTMX one. */
    data class RedirectTo(
        val url: String,
        override val volume: FailureVolume,
    ) : FailureResponse {
        override val status: HttpStatusCode get() = HttpStatusCode.Found
    }

    /** One out-of-band `<p>` per failed field, swapped next to the input that failed. */
    data class ValidationMessages(val errors: List<ValidationFailure>) : FailureResponse {
        override val volume: FailureVolume get() = FailureVolume.SILENT
        override val status: HttpStatusCode get() = errors.toHttpStatusCode()
    }
}

/**
 * The one table. Pure so that it can be pinned without a Ktor call: [requestUri] is where
 * `MissingToken` sends the user back to after sign-in (the page, not the fragment, under HTMX —
 * see `pageUri`), [reference] the call id quoted on the
 * generic error so a user can name something we can search for (`Monitoring.kt`).
 */
internal fun AppFailure.toFailureResponse(requestUri: String, reference: String?): FailureResponse {
    return when (this) {
        // Normal control flow. The user typed something wrong, or is not signed in yet.
        is AppFailure.ValidationError -> FailureResponse.ValidationMessages(errors)
        is AppFailure.Redirect -> FailureResponse.RedirectTo(toUrl(), FailureVolume.SILENT)
        is AppFailure.AuthError.MissingToken -> FailureResponse.RedirectTo(
            signInRedirectUrl(requestUri), FailureVolume.SILENT
        )

        // Expected but worth being able to count: an expired token, a link to something deleted.
        is AppFailure.AuthError.ConvertTokenError -> FailureResponse.RedirectTo(toRedirect().toUrl(), FailureVolume.INFO)
        // Split out of the generic branch (MCO-350): the copy used to say "an unexpected error
        // occurred", so navigating to a deleted world read as a crash. Nothing is broken here.
        is AppFailure.DatabaseError.NotFound -> FailureResponse.Alert(
            id = "not-found-error",
            title = "Not found",
            message = "That no longer exists. It may have been deleted.",
            status = HttpStatusCode.NotFound,
            volume = FailureVolume.INFO,
        )

        // Someone reached for something that is not theirs. Rarely an attack, always worth seeing.
        is AppFailure.AuthError.NotAuthorized -> FailureResponse.Alert(
            id = "not-authorized-error",
            title = "Not Authorized",
            message = "You do not have permission to perform this action.",
            status = HttpStatusCode.Forbidden,
            volume = FailureVolume.WARN,
        )

        // Genuinely broken.
        is AppFailure.AuthError.CouldNotCreateToken -> FailureResponse.Alert(
            id = "token-creation-error",
            title = "Authentication Error",
            message = "An error occurred while creating your authentication token. Please try signing in again.",
            status = HttpStatusCode.InternalServerError,
            volume = FailureVolume.ERROR,
        )
        is AppFailure.DatabaseError,
        is AppFailure.ApiError,
        is AppFailure.FileError,
        is AppFailure.IllegalConfigurationError -> FailureResponse.Alert(
            id = "generic-error",
            title = UNEXPECTED_ERROR_TITLE,
            message = unexpectedErrorMessage(reference),
            status = HttpStatusCode.InternalServerError,
            volume = FailureVolume.ERROR,
        )
    }
}

internal const val UNEXPECTED_ERROR_TITLE = "An error occurred"

/** Shared with the uncaught-exception boundary in `Routing.kt`, which has no [AppFailure]. */
internal fun unexpectedErrorMessage(reference: String?): String {
    val message = "An unexpected error occurred. Please try again later."
    return reference?.let { "$message (reference: $it)" } ?: message
}

/**
 * Records a failure at the one place every `handlePipeline` failure passes through (MCO-350).
 *
 * `defaultHandleError` used to log nothing at all. Failures built without an exception —
 * `MinecraftSignInPipeline`, `ItemSourceGraphSteps`, `GetDraftStep`, `ApiStore` — left no trace
 * whatsoever, and the user was told "the error has been logged" while nothing had been.
 *
 * What goes in the line is governed by documentation/logging.md: the failure type, the method and
 * **path** (never `uri`, which carries the query string), and the user id when one is established.
 * The `AppFailure` variants are `data object`s with no cause field, so there is no exception here
 * to leak even by accident — and the call id arrives on its own via MDC, which is what ties this
 * line to the id printed on the user's error page.
 */
private fun ApplicationCall.logFailure(error: AppFailure, volume: FailureVolume) {
    if (volume == FailureVolume.SILENT) return

    val userId = attributes.getOrNull(AttributeKey<TokenProfile>("user"))?.id
    val message = "Pipeline failure {} on {} {} (user {})"
    val args = arrayOf<Any?>(error, request.httpMethod.value, request.path(), userId ?: "anonymous")

    when (volume) {
        FailureVolume.ERROR -> errorLogger.error(message, *args)
        FailureVolume.WARN -> errorLogger.warn(message, *args)
        FailureVolume.INFO -> errorLogger.info(message, *args)
        FailureVolume.SILENT -> Unit
    }
}

suspend fun <E : AppFailure> ApplicationCall.defaultHandleError(error: E) {
    val response = error.toFailureResponse(requestUri = pageUri(), reference = callId)
    logFailure(error, response.volume)

    when (response) {
        is FailureResponse.Alert -> respondRefusal(response.status, response.title, response.message, response.id)
        is FailureResponse.RedirectTo -> redirectClientOrBrowser(response.url)
        is FailureResponse.ValidationMessages -> respondValidationMessages(response)
    }
}

/**
 * Under HTMX, each message lands next to its field in the form that sent the request
 * ([fieldMessages]). A plain form post has no fragment to swap, so it gets the status page with
 * the messages, rather than the fragments rendered as a page.
 */
private suspend fun ApplicationCall.respondValidationMessages(response: FailureResponse.ValidationMessages) {
    if (isHtmxRequest()) {
        respondHtml(fieldMessages(response.errors), response.status)
    } else {
        respondRefusal(
            response.status,
            "Check the form",
            response.errors.joinToString(" ") { it.userMessage() },
            alertId = "validation-error",
        )
    }
}

private fun List<ValidationFailure>.toHttpStatusCode(): HttpStatusCode {
    val distinctCodes = this.map { when(it) {
        is ValidationFailure.MissingParameter -> HttpStatusCode.BadRequest
        is ValidationFailure.InvalidFormat -> HttpStatusCode.UnprocessableEntity
        is ValidationFailure.OutOfRange -> HttpStatusCode.UnprocessableEntity
        is ValidationFailure.InvalidLength -> HttpStatusCode.UnprocessableEntity
        is ValidationFailure.InvalidValue -> HttpStatusCode.UnprocessableEntity
        is ValidationFailure.CustomValidation -> HttpStatusCode.UnprocessableEntity
    } }.distinct()

    if (distinctCodes.size == 1) {
        return distinctCodes.first()
    }

    return HttpStatusCode.BadRequest
}

/**
 * A refusal the person who triggered it can see, whichever way the request arrived (MCO-158,
 * MCO-436). Route plugins answer with this, and so does every [FailureResponse.Alert].
 *
 * Under HTMX: the standard alert, prepended to the alert container out of band. htmx swaps no
 * error response into its target (`noSwap` in `Layout.kt`), and it does that by setting the swap
 * to `none` after reading `HX-Retarget` / `HX-Reswap`, so a retargeted alert is silenced too.
 * Out-of-band swaps run whatever the status, and whatever the sender's `hx-status` says.
 *
 * Anything else is a page load (a typed URL, a link, a bookmark) and gets the status page with
 * the app's chrome. A bare alert fragment there renders as one unstyled sentence.
 *
 * A 404 under HTMX only survives because the `StatusPages` 404 handler stands aside for HTMX
 * requests (`Routing.kt`); it replaces every other 404 body with the full page.
 */
suspend fun ApplicationCall.respondRefusal(status: HttpStatusCode, title: String, message: String, alertId: String) {
    if (isHtmxRequest()) {
        // A non-outerHTML out-of-band swap inserts the element's children, so the <ul> is the
        // wrapper htmx strips and the <li> is what lands.
        respondHtml(createHTML().ul {
            hxOutOfBands("afterbegin:#$ALERT_CONTAINER_ID")
            li {
                createAlert(
                    id = alertId,
                    title = title,
                    message = message,
                    type = AlertType.ERROR
                )
            }
        }, statusCode = status)
    } else {
        respondHtml(statusPage(status, message), statusCode = status)
    }
}

/**
 * A request the page should not have been able to send: a parameter missing or unreadable, or a
 * choice that no longer exists. In practice the page is out of date, so the default says that.
 */
suspend fun ApplicationCall.respondBadRequest(message: String = STALE_PAGE_MESSAGE) =
    respondRefusal(HttpStatusCode.BadRequest, "That did not work", message, alertId = "bad-request-error")

internal const val STALE_PAGE_MESSAGE = "This part of the page is out of date. Reload it and try again."

/**
 * An error answered by re-rendering part of the page: [html] replaces [target] with [swap], the
 * way the success response would, e.g. a form shown again with its complaint at the top.
 *
 * Sent as an htmx partial, because htmx swaps no error response into its target (`noSwap`,
 * `Layout.kt`) and that also silences `HX-Retarget`. A partial is swapped whatever the status, and
 * [target] is resolved from the element that sent the request, so `closest form` works.
 */
suspend fun ApplicationCall.respondInPlace(
    html: String,
    target: String,
    swap: String = "outerHTML",
    status: HttpStatusCode = HttpStatusCode.UnprocessableEntity,
) {
    respondHtml(createHTML().hxPartial(target = target, swap = swap) { unsafe { +html } }, status)
}

private fun ApplicationCall.statusPage(status: HttpStatusCode, message: String): String = when {
    status == HttpStatusCode.NotFound -> notFoundPage()
    status == HttpStatusCode.Forbidden -> forbiddenPage(message)
    status.value >= 500 -> serverErrorPage(callId)
    else -> errorPageLayout(
        pageTitle = "${status.value} — ${status.description} · Seam",
        heading = "${status.value} — ${status.description}",
        body = message,
        ctaText = "Back to worlds",
        ctaHref = "/worlds",
    )
}
