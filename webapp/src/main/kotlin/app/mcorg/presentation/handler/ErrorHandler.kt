package app.mcorg.presentation.handler

import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.layout.alert.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.layout.alert.AlertType
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.hxSwap
import app.mcorg.presentation.utils.hxTarget
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML

sealed interface ErrorHandler {
    data class AlertPopup(
        val id: String,
        val title: String,
        val message: String? = null,
        val type: AlertType = AlertType.ERROR,
        val statusCode : HttpStatusCode = HttpStatusCode.InternalServerError
    ) : ErrorHandler
}

suspend fun <E : AppFailure> ApplicationCall.defaultHandleError(error: E) {
    when (error) {
        is AppFailure.ValidationError -> handleValidationMessage(error.errors)
        is AppFailure.DatabaseError,
        is AppFailure.ApiError,
        is AppFailure.FileError,
        is AppFailure.IllegalConfigurationError -> handleErrorMessage(
            ErrorHandler.AlertPopup(
                id = "generic-error",
                title = "An error occurred",
                message = "An unexpected error occurred. Please try again later.",
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

