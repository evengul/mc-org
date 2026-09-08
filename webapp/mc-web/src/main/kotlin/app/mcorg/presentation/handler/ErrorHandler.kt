package app.mcorg.presentation.handler

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.dsl.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.dsl.AlertType
import app.mcorg.presentation.templated.dsl.createAlert
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.hxSwap
import app.mcorg.presentation.utils.hxTarget
import app.mcorg.presentation.utils.respondHtml
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
 * `MissingToken` sends the user back to after sign-in, [reference] the call id quoted on the
 * generic error so a user can name something we can search for (`Monitoring.kt`).
 */
internal fun AppFailure.toFailureResponse(requestUri: String, reference: String?): FailureResponse {
    fun referenced(message: String) = reference?.let { "$message (reference: $it)" } ?: message

    return when (this) {
        // Normal control flow. The user typed something wrong, or is not signed in yet.
        is AppFailure.ValidationError -> FailureResponse.ValidationMessages(errors)
        is AppFailure.Redirect -> FailureResponse.RedirectTo(toUrl(), FailureVolume.SILENT)
        is AppFailure.AuthError.MissingToken -> FailureResponse.RedirectTo(
            "/auth/sign-in?redirect_to=$requestUri", FailureVolume.SILENT
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
            title = "An error occurred",
            message = referenced("An unexpected error occurred. Please try again later."),
            status = HttpStatusCode.InternalServerError,
            volume = FailureVolume.ERROR,
        )
    }
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
    val response = error.toFailureResponse(requestUri = request.uri, reference = callId)
    logFailure(error, response.volume)

    when (response) {
        is FailureResponse.Alert -> respondAlert(response)
        is FailureResponse.RedirectTo -> respondRedirectFor(response.url)
        is FailureResponse.ValidationMessages -> respondValidationMessages(response)
    }
}

private suspend fun ApplicationCall.respondRedirectFor(url: String) {
    if (request.headers["HX-Request"] == "true") {
        clientRedirect(url)
    } else {
        respondRedirect(url)
    }
}

private suspend fun ApplicationCall.respondValidationMessages(response: FailureResponse.ValidationMessages) {
    respondHtml(statusCode = response.status, html = createHTML().div {
        response.errors.forEach {
            p {
                hxOutOfBands("true")
                classes += "validation-error-message"
                id = "validation-error-${it.parameterName.replace("[]", "")}"
                +it.userMessage()
            }
        }
    })
}

private fun ValidationFailure.userMessage(): String = when (this) {
    is ValidationFailure.CustomValidation -> message
    is ValidationFailure.InvalidFormat -> message ?: ""
    is ValidationFailure.InvalidLength -> when {
        minLength != null && maxLength != null ->
            "The length of '$parameterName' must be between $minLength and $maxLength characters."
        minLength != null -> "The length of '$parameterName' must be at least $minLength characters."
        maxLength != null -> "The length of '$parameterName' must be at most $maxLength characters."
        else -> ""
    }
    is ValidationFailure.InvalidValue -> when {
        allowedValues != null ->
            "The value of '$parameterName' must be one of the following: ${allowedValues.joinToString(", ")}."
        else -> "The value of '$parameterName' is invalid."
    }
    is ValidationFailure.MissingParameter -> "The parameter '$parameterName' is required."
    is ValidationFailure.OutOfRange -> when {
        min != null && max != null -> "The value of '$parameterName' must be between $min and $max."
        min != null -> "The value of '$parameterName' must be at least $min."
        max != null -> "The value of '$parameterName' must be at most $max."
        else -> ""
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

private suspend fun ApplicationCall.respondAlert(alert: FailureResponse.Alert) {
    hxTarget("#$ALERT_CONTAINER_ID")
    hxSwap("afterbegin")
    respondHtml(createHTML().li {
        createAlert(
            id = alert.id,
            title = alert.title,
            message = alert.message,
            type = AlertType.ERROR
        )
    }, statusCode = alert.status)
}
