package app.mcorg.pipeline.failure

import app.mcorg.domain.pipeline.Step
import java.net.URLEncoder

sealed interface AppFailure {

    sealed interface AuthError : AppFailure {
        data object NotAuthorized : AuthError
        data object MissingToken : AuthError
        data object CouldNotCreateToken : AuthError

        data class ConvertTokenError(
            val errorCode: String,
            val arguments: List<Pair<String, String>> = emptyList()
        ) : AuthError {
            companion object {
                fun invalidToken() = ConvertTokenError("invalid_token")
                fun expiredToken() = ConvertTokenError("expired_token")
                fun missingClaim(claimName: String) = ConvertTokenError(
                    "missing_claim", listOf("claim" to claimName)
                )
                fun incorrectClaim(claimName: String, claimValue: String) = ConvertTokenError(
                    "incorrect_claim", listOf("claim" to claimName, "value" to claimValue)
                )
                fun conversionError() = ConvertTokenError("conversion_error")
            }

            fun toRedirect() = Redirect(
                path = "/auth/sign-out",
                queryParameters = buildMap {
                    put("error", errorCode)
                    arguments.forEach { (key, value) ->
                        put(key, value)
                    }
                }
            )
        }
    }

    sealed interface DatabaseError : AppFailure {
        data object ConnectionError : DatabaseError
        data object StatementError : DatabaseError
        data object IntegrityConstraintError : DatabaseError
        data object ResultMappingError : DatabaseError
        data object UnknownError : DatabaseError
        data object NoIdReturned : DatabaseError
        data object NotFound : DatabaseError
    }

    sealed interface ApiError : AppFailure {
        data object NetworkError : ApiError
        data object TimeoutError : ApiError
        data object RateLimitExceeded : ApiError
        data class HttpError(val statusCode: Int, val body: String? = null) : ApiError
        data object SerializationError : ApiError
        data object UnknownError : ApiError
    }

    data class ValidationError(val errors: List<ValidationFailure>) : AppFailure

    data class Redirect(
        val path: String,
        val queryParameters: Map<String, String> = emptyMap()
    ) : AppFailure {
        fun toUrl(): String {
            if (queryParameters.isEmpty()) {
                return path
            }
            val queryString = queryParameters.entries.joinToString("&") { (key, value) ->
                "${key}=${URLEncoder.encode(value, "UTF-8")}"
            }
            return "$path?$queryString"
        }
    }

    data class IllegalConfigurationError(val reason: String) : AppFailure

    data class FileError(val source: Class<Step<*, *, *>>, val filename: String? = null) : AppFailure

    companion object {
        fun customValidationError(field: String, message: String) = ValidationError(
            listOf(ValidationFailure.CustomValidation(field, message))
        )
    }
}