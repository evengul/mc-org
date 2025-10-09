package app.mcorg.pipeline.failure

sealed interface SignInLocallyFailure
sealed interface SignInWithMinecraftFailure

sealed interface AuthPluginFailure

sealed interface GetSignInPageFailure
sealed interface GetSignInPageFailureResult

data class Redirect(val url: String) : AuthPluginFailure, GetSignInPageFailure, SignInWithMinecraftFailure, GetSignInPageFailureResult {
    companion object
}

data object MissingToken : GetSignInPageFailureResult

fun AuthPluginFailure.toRedirect(signInUrl: String): Redirect {
    return when (this) {
        is Redirect -> this
        is GetCookieFailure.MissingCookie -> Redirect.other(signInUrl)
        is ConvertTokenStepFailure.ConversionError -> Redirect.signOut("conversion")
        is ConvertTokenStepFailure.ExpiredToken -> Redirect.signOut("expired")
        is ConvertTokenStepFailure.InvalidToken -> Redirect.signOut("invalid")
        is ConvertTokenStepFailure.MissingClaim -> Redirect.signOut("missing_claim", "claim" to this.claimName)
        is ConvertTokenStepFailure.IncorrectClaim -> Redirect.signOut("incorrect_claim", "claim" to this.claimName, "value" to this.claimValue)
        is GetProfileFailure.ProfileNotFound -> Redirect.signOut("profile_not_found")
    }
}

fun GetSignInPageFailure.toRedirect(redirectUrl: String? = null): GetSignInPageFailureResult {
    return when (this) {
        is Redirect -> this
        is GetCookieFailure.MissingCookie -> MissingToken
        is ConvertTokenStepFailure.ConversionError -> Redirect.signOut("conversion")
        is ConvertTokenStepFailure.ExpiredToken -> Redirect.signOut("expired")
        is ConvertTokenStepFailure.InvalidToken -> Redirect.signOut("invalid")
        is ConvertTokenStepFailure.MissingClaim -> Redirect.signOut("missing_claim", "claim" to this.claimName)
        is ConvertTokenStepFailure.IncorrectClaim -> Redirect.signOut("incorrect_claim", "claim" to this.claimName, "value" to this.claimValue)
        is GetProfileFailure.ProfileNotFound -> Redirect.signOut("profile_not_found")
        is GetSelectedWorldStepFailure.NoSelectedWorld -> Redirect.other(redirectUrl ?: "/app")
    }
}

fun SignInWithMinecraftFailure.toRedirect(): Redirect {
    return when (this) {
        is Redirect -> this
        is GetMicrosoftCodeFailure.MissingCode -> Redirect.signOut("missing_code")
        is GetMicrosoftCodeFailure.Error -> Redirect.signOut("microsoft_code", "microsoft_error" to this.error, "description" to this.description)
        is GetMicrosoftTokenFailure.CouldNotGetToken -> Redirect.signOut("microsoft_token", "microsoft_error" to this.error, "description" to this.description)
        is GetMicrosoftTokenFailure.NoHostForNonLocalEnv -> Redirect.signOut("missing_host_variable", "description" to "Host variable is not set for non-local environment")
        is GetXboxProfileFailure.CouldNotGetXboxProfile -> Redirect.signOut("xbox_profile")
        is GetXstsTokenFailure.CouldNotGetXstsToken -> Redirect.signOut("xsts_token")
        is GetMinecraftTokenFailure.CouldNotGetMinecraftToken -> Redirect.signOut("minecraft_token")
        is GetMinecraftProfileFailure.CouldNotGetProfile -> Redirect.signOut("minecraft_profile")
        is CreateTokenFailure.CouldNotCreateToken -> Redirect.signOut("token_creation", "description" to (this.cause.message ?: "unknown"))
        is CreateUserIfNotExistsFailure.Other -> Redirect.signOut("user_creation", "description" to "Could not create user after signing in")
        is UpdateLastSignInFailure.Other -> Redirect.signOut("sign_in_error", "description" to "Error occurred while signing in")
    }
}

fun Redirect.Companion.other(url: String): Redirect = Redirect(url)
fun Redirect.Companion.signOut(error: String, vararg args: Pair<String, String>): Redirect =
    Redirect("/auth/sign-out?error=$error&args=${args.joinToString("&") { "${it.first}=${it.second}" }}")

sealed interface AddCookieFailure : SignInLocallyFailure, SignInWithMinecraftFailure

sealed interface ConvertTokenStepFailure : GetSignInPageFailure, AuthPluginFailure {
    data object InvalidToken : ConvertTokenStepFailure
    data object ExpiredToken : ConvertTokenStepFailure
    data class MissingClaim(val claimName: String) : ConvertTokenStepFailure
    data class IncorrectClaim(val claimName: String, val claimValue: String) : ConvertTokenStepFailure
    data class ConversionError(val error: Exception) : ConvertTokenStepFailure
}

sealed interface CreateTokenFailure : SignInLocallyFailure, SignInWithMinecraftFailure {
    data class CouldNotCreateToken(val cause: Throwable) : CreateTokenFailure
}

sealed interface CreateUserIfNotExistsFailure : SignInLocallyFailure, SignInWithMinecraftFailure {
    data class Other(val failure: DatabaseFailure) : CreateUserIfNotExistsFailure
}

sealed interface GetMicrosoftCodeFailure : SignInWithMinecraftFailure {
    data class Error(val error: String, val description: String) : GetMicrosoftCodeFailure
    data object MissingCode : GetMicrosoftCodeFailure
}

sealed interface GetMicrosoftTokenFailure : SignInWithMinecraftFailure {
    data object NoHostForNonLocalEnv : GetMicrosoftTokenFailure
    data class CouldNotGetToken(val error: String, val description: String) : GetMicrosoftTokenFailure
}

sealed interface GetMinecraftProfileFailure : SignInWithMinecraftFailure {
    data object CouldNotGetProfile : GetMinecraftProfileFailure
}

sealed interface GetMinecraftTokenFailure : SignInWithMinecraftFailure {
    data object CouldNotGetMinecraftToken : GetMinecraftTokenFailure
}

sealed interface GetProfileFailure : GetSignInPageFailure, AuthPluginFailure {
    data object ProfileNotFound : GetProfileFailure
}

sealed interface GetSelectedWorldStepFailure : GetSignInPageFailure {
    data object NoSelectedWorld : GetSelectedWorldStepFailure
}

sealed interface GetCookieFailure : GetSignInPageFailure, AuthPluginFailure {
    data object MissingCookie : GetCookieFailure
}

sealed interface GetXboxProfileFailure : SignInWithMinecraftFailure {
    data object CouldNotGetXboxProfile : GetXboxProfileFailure
}

sealed interface GetXstsTokenFailure : SignInWithMinecraftFailure {
    data object CouldNotGetXstsToken : GetXstsTokenFailure
}

sealed interface UpdateLastSignInFailure : SignInLocallyFailure, SignInWithMinecraftFailure {
    data class Other(val failure: DatabaseFailure) : UpdateLastSignInFailure
}

sealed interface ValidateEnvFailure : SignInLocallyFailure {
    data object InvalidEnv : ValidateEnvFailure
}