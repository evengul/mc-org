package app.mcorg.pipeline.auth

sealed interface SignInLocallyFailure

sealed interface AuthPluginFailure

sealed interface GetSignInPageFailure

data class Redirect(val url: String) : AuthPluginFailure, GetSignInPageFailure

fun AuthPluginFailure.toRedirect(signInUrl: String = "/auth/sign-in", signOutUrl: String = "/auth/sign-out"): Redirect {
    return when (this) {
        is Redirect -> this
        is GetCookieFailure.MissingCookie -> Redirect(url = signInUrl)
        is ConvertTokenStepFailure.ConversionError -> Redirect("$signOutUrl?error=conversion")
        is ConvertTokenStepFailure.ExpiredToken -> Redirect("$signOutUrl?error=expired")
        is ConvertTokenStepFailure.InvalidToken -> Redirect("$signOutUrl?error=invalid")
        is ConvertTokenStepFailure.MissingClaim -> Redirect("$signOutUrl?error=missing_claim&claim=${this.claimName}")
        is ConvertTokenStepFailure.IncorrectClaim -> Redirect("$signOutUrl?error=incorrect_claim&claim=${this.claimName}&value=${this.claimValue}")
    }
}

fun GetSignInPageFailure.toRedirect(signInUrl: String = "/auth/sign-in", signOutUrl: String = "/auth/sign-out"): Redirect {
    return when (this) {
        is Redirect -> this
        is GetCookieFailure.MissingCookie -> Redirect(url = signInUrl)
        is ConvertTokenStepFailure.ConversionError -> Redirect("$signOutUrl?error=conversion")
        is ConvertTokenStepFailure.ExpiredToken -> Redirect("$signOutUrl?error=expired")
        is ConvertTokenStepFailure.InvalidToken -> Redirect("$signOutUrl?error=invalid")
        is ConvertTokenStepFailure.MissingClaim -> Redirect("$signOutUrl?error=missing_claim&claim=${this.claimName}")
        is ConvertTokenStepFailure.IncorrectClaim -> Redirect("$signOutUrl?error=incorrect_claim&claim=${this.claimName}&value=${this.claimValue}")
        is GetProfileFailure.ProfileNotFound -> Redirect("$signOutUrl?error=profile_not_found")
        is GetSelectedWorldStepFailure.NoSelectedWorld -> Redirect("/app/worlds/add")
    }
}
