package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.RedirectStep
import app.mcorg.pipeline.auth.ConvertTokenStep
import app.mcorg.pipeline.auth.GetProfileStepForAuth
import app.mcorg.pipeline.auth.GetSelectedWorldIdStep
import app.mcorg.pipeline.auth.GetSignInPageFailure
import app.mcorg.pipeline.auth.GetTokenStep
import app.mcorg.pipeline.auth.MissingToken
import app.mcorg.pipeline.auth.Redirect
import app.mcorg.pipeline.auth.toRedirect
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import io.ktor.server.application.*
import io.ktor.server.request.RequestCookies
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetLanding() {
    val result = Pipeline.create<GetSignInPageFailure, RequestCookies>()
        .pipe(GetTokenStep(AUTH_COOKIE))
        .pipe(ConvertTokenStep(ISSUER))
        .pipe(GetProfileStepForAuth)
        .pipe(GetSelectedWorldIdStep)
        .pipe(RedirectStep {
            "/app/worlds/$it/projects"
        })
        .mapFailure { it.toRedirect() }
        .execute(request.cookies)

    when(result) {
        is Result.Failure -> when(result.error) {
            is MissingToken -> respondRedirect("/auth/sign-in")
            is Redirect -> respondRedirect(result.error.url)
        }
        is Result.Success -> respondRedirect(result.value)
    }
}