package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.pipeline.auth.commonsteps.ConvertTokenStep
import app.mcorg.pipeline.auth.commonsteps.GetTokenStep
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import io.ktor.server.application.*
import io.ktor.server.request.RequestCookies
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetLanding() {
    Pipeline.create<String, RequestCookies>()
        .wrapPipe(GetTokenStep(AUTH_COOKIE)) {
            it.mapError { "/auth/sign-in" }
        }
        .wrapPipe(ConvertTokenStep(ISSUER)) {
            it.mapError { e -> e.toSignOutUrl() }
        }
        .fold(
            input = request.cookies,
            onSuccess = { respondRedirect("/app") },
            onFailure = { respondRedirect(it) }
        )
}