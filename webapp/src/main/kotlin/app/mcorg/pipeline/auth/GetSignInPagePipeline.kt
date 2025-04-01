package app.mcorg.pipeline.auth

import app.mcorg.domain.api.Users
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.pipeline.RedirectStep
import io.ktor.server.request.RequestCookies

object AuthPipelines {
    private const val AUTH_COOKIE = "MCORG-USER-TOKEN"
    private const val ISSUER = "mcorg"

    fun getSignInPagePipeline(
        signInUrl: String,
        signOutUrl: String,
        usersApi: Users,
    ): Pipeline<RequestCookies, AuthFailureWithRedirect, String> {
        return Pipeline.create<AuthFailure, RequestCookies>()
            .pipe(GetTokenStep(AUTH_COOKIE))
            .pipe(ConvertCookieStep(ISSUER))
            .pipe(GetProfileStep(usersApi))
            .pipe(GetSelectedWorldIdStep)
            .pipe(RedirectStep {
                "/app/worlds/$it/projects"
            })
            .mapFailure {
                return@mapFailure when (it) {
                    is GetTokenStepFailure.TokenNotFound -> AuthFailureWithRedirect(signInUrl)
                    is ConvertCookieStepFailure.InvalidToken -> AuthFailureWithRedirect("$signOutUrl?error=invalid_token")
                    is ConvertCookieStepFailure.ExpiredToken -> AuthFailureWithRedirect("$signOutUrl?error=expired_token")
                    is ConvertCookieStepFailure.MissingClaim -> AuthFailureWithRedirect("$signOutUrl?error=missing_claim&claim=${it.claim}")
                    is ConvertCookieStepFailure.ConversionError -> AuthFailureWithRedirect("$signOutUrl?error=conversion_error")
                    is Unauthorized -> AuthFailureWithRedirect("$signOutUrl?error=unauthorized")
                    is GetProfileFailure.ProfileNotFound -> AuthFailureWithRedirect("$signOutUrl?error=profile_not_found")
                    is GetSelectedWorldStepFailure.NoSelectedWorld -> AuthFailureWithRedirect("/app/worlds/add")
                    is AuthFailureWithRedirect -> it
                }
            }
    }
}
