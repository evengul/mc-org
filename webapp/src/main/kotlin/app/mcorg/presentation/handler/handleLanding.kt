package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.pipeline.auth.ConvertTokenStep
import app.mcorg.pipeline.failure.GetSignInPageFailure
import app.mcorg.pipeline.auth.GetTokenStep
import app.mcorg.pipeline.failure.MissingToken
import app.mcorg.pipeline.failure.Redirect
import app.mcorg.pipeline.failure.toRedirect
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import io.ktor.server.application.*
import io.ktor.server.request.RequestCookies
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetLanding() {
    Pipeline.create<GetSignInPageFailure, RequestCookies>()
        .pipe(GetTokenStep(AUTH_COOKIE))
        .pipe(ConvertTokenStep(ISSUER))
        .mapFailure { it.toRedirect() }
        .fold(
            input = request.cookies,
            onSuccess = { respondRedirect("/app") },
            onFailure = { when(it) {
                is MissingToken -> respondRedirect("/auth/sign-in")
                is Redirect -> respondRedirect(it.url)
            } }
        )
}