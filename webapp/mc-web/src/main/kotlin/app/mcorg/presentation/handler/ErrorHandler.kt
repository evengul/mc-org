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

sealed interface ErrorHandler {
    data class AlertPopup(
        val id: String,
        val title: String,
        val message: String? = null,
        val type: AlertType = AlertType.ERROR,
        val statusCode : HttpStatusCode = HttpStatusCode.InternalServerError
    ) : ErrorHandler
}

private val errorLogger = LoggerFactory.getLogger("app.mcorg.presentation.ErrorBoundary")

/**
 * How loudly a failure reaching the boundary deserves to be reported.
 *
 * Most of what arrives here is ordinary: a signed-out visitor, a stale link, a form filled in
 * wrong. Logging all of it at ERROR would make the error log useless on the day it matters, which
 * is the failure mode this issue is trying to avoid — not replace with a noisier one.
 */
private enum class FailureVolume { SILENT, INFO, WARN, ERROR }

private fun AppFailure.volume(): FailureVolume = when (this) {
    // Normal control flow. The user typed something wrong, or is not signed in yet.
    is AppFailure.ValidationError -> FailureVolume.SILENT
    is AppFailure.Redirect -> FailureVolume.SILENT
    is AppFailure.AuthError.MissingToken -> FailureVolume.SILENT

    // Expected but worth being able to count: an expired token, a link to something deleted.
    is AppFailure.AuthError.ConvertTokenError -> FailureVolume.INFO
    is AppFailure.DatabaseError.NotFound -> FailureVolume.INFO

    // Someone reached for something that is not theirs. Rarely an attack, always worth seeing.
    is AppFailure.AuthError.NotAuthorized -> FailureVolume.WARN

    // Genuinely broken.
    is AppFailure.AuthError.CouldNotCreateToken -> FailureVolume.ERROR
    is AppFailure.DatabaseError -> FailureVolume.ERROR
    is AppFailure.ApiError -> FailureVolume.ERROR
    is AppFailure.FileError -> FailureVolume.ERROR
    is AppFailure.IllegalConfigurationError -> FailureVolume.ERROR
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
private fun ApplicationCall.logFailure(error: AppFailure) {
    val volume = error.volume()
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
    logFailure(error)

    when (error) {
        is AppFailure.ValidationError -> handleValidationMessage(error.errors)

        // Split out of the generic branch (MCO-350). toHttpStatusCode already returned 404, but
        // the copy said "an unexpected error occurred", so navigating to a deleted world read as
        // a crash. Nothing is broken here and the page should not imply otherwise.
        is AppFailure.DatabaseError.NotFound -> handleErrorMessage(
            ErrorHandler.AlertPopup(
                id = "not-found-error",
                title = "Not found",
                message = "That no longer exists. It may have been deleted.",
                statusCode = HttpStatusCode.NotFound
            )
        )

        is AppFailure.DatabaseError,
        is AppFailure.ApiError,
        is AppFailure.FileError,
        is AppFailure.IllegalConfigurationError -> handleErrorMessage(
            ErrorHandler.AlertPopup(
                id = "generic-error",
                title = "An error occurred",
                // The call id is the whole point of the issue: it lets a user quote something we
                // can search for, instead of describing what they were doing.
                message = referenceSuffixed("An unexpected error occurred. Please try again later."),
                statusCode = error.toHttpStatusCode()
            )
        )

        is AppFailure.Redirect -> {
            if (request.headers["HX-Request"] == "true") {
                clientRedirect(error.toUrl())
            } else {
                respondRedirect(error.toUrl())
            }
        }

        is AppFailure.AuthError -> when (error) {
            is AppFailure.AuthError.MissingToken -> respondRedirect("/auth/sign-in?redirect_to=${request.uri}")
            is AppFailure.AuthError.NotAuthorized -> handleErrorMessage(
                ErrorHandler.AlertPopup(
                    id = "not-authorized-error",
                    title = "Not Authorized",
                    message = "You do not have permission to perform this action.",
                    statusCode = HttpStatusCode.Forbidden
                )
            )
            is AppFailure.AuthError.CouldNotCreateToken -> handleErrorMessage(
                ErrorHandler.AlertPopup(
                    id = "token-creation-error",
                    title = "Authentication Error",
                    message = "An error occurred while creating your authentication token. Please try signing in again.",
                )
            )
            is AppFailure.AuthError.ConvertTokenError -> handleRedirect(error.toRedirect().toUrl())
        }
    }
}

/**
 * Appends this call's id to a user-facing message, when there is one.
 *
 * The id is opaque and generated server-side (`Monitoring.kt`), so it is safe to show — it says
 * nothing about the user or the failure, it is only a handle for finding the log line. Absent in
 * tests that do not install `CallId`, hence the null branch rather than a fabricated value.
 */
private fun ApplicationCall.referenceSuffixed(message: String): String =
    callId?.let { "$message (reference: $it)" } ?: message

private suspend fun ApplicationCall.handleRedirect(url: String) {
    if (request.headers["HX-Request"] == "true") {
        clientRedirect(url)
    } else {
        respondRedirect(url)
    }
}

private suspend fun ApplicationCall.handleValidationMessage(errors: List<ValidationFailure>) {
    respondHtml(statusCode = errors.toHttpStatusCode(), html = createHTML().div {
        errors.forEach {
            p {
                hxOutOfBands("true")
                classes += "validation-error-message"
                id = "validation-error-${it.parameterName.replace("[]", "")}"

                when (it) {
                    is ValidationFailure.CustomValidation -> (+it.message)
                    is ValidationFailure.InvalidFormat -> it.message?.let { msg -> +(msg) }
                    is ValidationFailure.InvalidLength -> {
                        when {
                            it.minLength != null && it.maxLength != null -> {
                                +"The length of '${it.parameterName}' must be between ${it.minLength} and ${it.maxLength} characters."
                            }

                            it.minLength != null -> {
                                +"The length of '${it.parameterName}' must be at least ${it.minLength} characters."
                            }

                            it.maxLength != null -> {
                                +"The length of '${it.parameterName}' must be at most ${it.maxLength} characters."
                            }
                        }
                    }

                    is ValidationFailure.InvalidValue -> {
                        when {
                            it.allowedValues != null -> {
                                +"The value of '${it.parameterName}' must be one of the following: ${
                                    it.allowedValues.joinToString(
                                        ", "
                                    )
                                }."
                            }

                            else -> {
                                +"The value of '${it.parameterName}' is invalid."
                            }
                        }
                    }

                    is ValidationFailure.MissingParameter -> {
                        +"The parameter '${it.parameterName}' is required."
                    }

                    is ValidationFailure.OutOfRange -> {
                        when {
                            it.min != null && it.max != null -> {
                                +"The value of '${it.parameterName}' must be between ${it.min} and ${it.max}."
                            }

                            it.min != null -> {
                                +"The value of '${it.parameterName}' must be at least ${it.min}."
                            }

                            it.max != null -> {
                                +"The value of '${it.parameterName}' must be at most ${it.max}."
                            }
                        }
                    }
                }
            }
        }
    })
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

private suspend fun ApplicationCall.handleErrorMessage(alertPopup: ErrorHandler.AlertPopup) {
    hxTarget("#$ALERT_CONTAINER_ID")
    hxSwap("afterbegin")
    respondHtml(createHTML().li {
        createAlert(
            id = alertPopup.id,
            title = alertPopup.title,
            message = alertPopup.message,
            type = alertPopup.type
        )
    }, statusCode = alertPopup.statusCode)
}

private fun AppFailure.toHttpStatusCode(): HttpStatusCode {
    return when (this) {
        is AppFailure.ValidationError -> this.errors.toHttpStatusCode()
        is AppFailure.DatabaseError.NotFound -> HttpStatusCode.NotFound
        is AppFailure.DatabaseError -> HttpStatusCode.InternalServerError
        is AppFailure.ApiError -> HttpStatusCode.InternalServerError
        is AppFailure.FileError -> HttpStatusCode.InternalServerError
        is AppFailure.IllegalConfigurationError -> HttpStatusCode.InternalServerError
        is AppFailure.Redirect -> HttpStatusCode.Found
        is AppFailure.AuthError -> when (this) {
            is AppFailure.AuthError.MissingToken -> HttpStatusCode.Unauthorized
            is AppFailure.AuthError.NotAuthorized -> HttpStatusCode.Forbidden
            is AppFailure.AuthError.CouldNotCreateToken -> HttpStatusCode.InternalServerError
            is AppFailure.AuthError.ConvertTokenError -> HttpStatusCode.Found
        }
    }
}

