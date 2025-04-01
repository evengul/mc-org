package app.mcorg.pipeline.auth

sealed interface AuthFailure

sealed interface GetTokenStepFailure : AuthFailure {
    object TokenNotFound : GetTokenStepFailure
}

sealed interface ConvertCookieStepFailure : AuthFailure {
    object InvalidToken : ConvertCookieStepFailure
    object ExpiredToken : ConvertCookieStepFailure
    data class MissingClaim(val claim: String) : ConvertCookieStepFailure
    data class ConversionError(val cause: Throwable) : ConvertCookieStepFailure
}

sealed interface GetProfileFailure : AuthFailure {
    object ProfileNotFound : GetProfileFailure
}

sealed interface GetSelectedWorldStepFailure : AuthFailure {
    object NoSelectedWorld : GetSelectedWorldStepFailure
}

data class AuthFailureWithRedirect(val url: String): AuthFailure

object Unauthorized : AuthFailure