package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.pipeline
import app.mcorg.pipeline.auth.commonsteps.ConvertTokenStep
import app.mcorg.pipeline.auth.commonsteps.GetTokenStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetLanding() {
    pipeline(
        onSuccess = {
            respondRedirect("/worlds")
        },
        onFailure = { error: AppFailure ->
            when (error) {
                is AppFailure.AuthError.MissingToken -> respondRedirect("/auth/sign-in")
                is AppFailure.AuthError.ConvertTokenError -> respondRedirect(error.toRedirect().toUrl())
                else -> respondRedirect("/auth/sign-out?error=unknown_error")
            }
        }
    ) {
        val token = GetTokenStep(AUTH_COOKIE).run(request.cookies)
        ConvertTokenStep(ISSUER).run(token)
    }
}
