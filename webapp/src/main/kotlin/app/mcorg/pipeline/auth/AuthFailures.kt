package app.mcorg.pipeline.auth

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
        is GetSelectedWorldStepFailure.NoSelectedWorld -> Redirect.other(redirectUrl ?: "/app/worlds")
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
