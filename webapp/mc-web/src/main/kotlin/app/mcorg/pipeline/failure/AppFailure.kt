package app.mcorg.pipeline.failure

import app.mcorg.pipeline.Step
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
                fun invalidToken() = ConvertTokenError(SignOutReason.INVALID_TOKEN.code)
                fun expiredToken() = ConvertTokenError(SignOutReason.EXPIRED_TOKEN.code)
                fun missingClaim(claimName: String) = ConvertTokenError(
                    "missing_claim", listOf("claim" to claimName)
                )
                fun incorrectClaim(claimName: String, claimValue: String) = ConvertTokenError(
                    "incorrect_claim", listOf("claim" to claimName, "value" to claimValue)
                )
                fun conversionError() = ConvertTokenError("conversion_error")
            }

            // Only the reason travels, never errorCode or arguments: the arguments include a claim
            // value read from a token the client sent, and a URL puts it in browser history and
            // Referer headers (MCO-438). Telling a user *which* claim failed gives them nothing to
            // act on, so every failure but expiry reads the same.
            fun toRedirect(): Redirect =
                if (errorCode == SignOutReason.EXPIRED_TOKEN.code) SignOutReason.EXPIRED_TOKEN.redirect()
                else SignOutReason.INVALID_TOKEN.redirect()
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
        data class HttpError(val statusCode: Int, val body: String? = null) : ApiError {
            // MCO-340. `body` is a verbatim upstream response — for the auth pipeline that is
            // Microsoft's token endpoint. This type is already interpolated into a log line by
            // cli/IngestServerFiles ("ingestion failed: ${result.error}"), where the body is
            // harmless Mojang manifest text; the point is that nothing stopped the same
            // interpolation appearing on the auth path. Keep the body reachable as a property
            // for callers that branch on it, but never let it render.
            override fun toString() = "HttpError(statusCode=$statusCode, body=<${body?.length ?: 0} chars>)"
        }
        data object SerializationError : ApiError
        data class ChecksumMismatch(val expected: String, val actual: String) : ApiError
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