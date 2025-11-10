package app.mcorg.presentation.handler

import app.mcorg.pipeline.auth.commonsteps.ConvertTokenStep
import app.mcorg.pipeline.auth.commonsteps.GetTokenStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetLanding() {
    executePipeline(
        onSuccess = {
            respondRedirect("/app")
        },
        onFailure = {
            when (it) {
                is AppFailure.AuthError.MissingToken -> respondRedirect("/auth/sign-in")
                is AppFailure.AuthError.ConvertTokenError -> respondRedirect(it.toRedirect().toUrl())
                else -> respondRedirect("/auth/sign-out?error=unknown_error")
            }
        }
    ) {
        value(request.cookies)
            .step(GetTokenStep(AUTH_COOKIE))
            .step(ConvertTokenStep(ISSUER))
    }
}