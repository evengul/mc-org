package app.mcorg.presentation.plugins

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.auth.AuthPluginFailure
import app.mcorg.pipeline.auth.ConvertTokenStep
import app.mcorg.pipeline.auth.GetCookieFailure
import app.mcorg.pipeline.auth.GetTokenStep
import app.mcorg.pipeline.auth.toRedirect
import app.mcorg.pipeline.project.EnsureUserExistsInProject
import app.mcorg.pipeline.project.EnsureUserExistsInProjectFailure
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.RequestCookies
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect

val AuthPlugin = createRouteScopedPlugin("AuthPlugin") {
    onCall {
        if (it.request.path().startsWith("/static/")
            || it.request.path().startsWith("/assets/")
            || it.request.path().startsWith("/favicon.ico")) {
            return@onCall
        }
        val result = Pipeline.create<AuthPluginFailure, RequestCookies>()
            .pipe(GetTokenStep(AUTH_COOKIE))
            .pipe(ConvertTokenStep(ISSUER))
            .map { user -> it.storeUser(user) }
            .execute(it.request.cookies)
        if (result is Result.Failure && result.error !is GetCookieFailure.MissingCookie) {
            it.response.cookies.removeToken(it.getHost() ?: "localhost")
            val url = result.error.toRedirect("/auth/sign-in?redirect_to=${it.request.path()}").url
            it.respondRedirect(url, permanent = false)
        } else if (result is Result.Failure && !it.request.path().contains("/auth/sign-in") && !it.request.path().contains("/oidc")) {
            it.respondRedirect("/auth/sign-in?redirect_to=${it.request.path()}", permanent = false)
        }
    }
}

val WorldParticipantPlugin = createRouteScopedPlugin("WorldAccessPlugin") {
    onCall {
        val userId = it.getUserId()
        val worldId = it.getWorldId()
        EnsureUserExistsInProject(worldId).process(userId).errorOrNull()?.also { error ->
            when (error) {
                is EnsureUserExistsInProjectFailure.UserNotFound -> it.respond(HttpStatusCode.Forbidden)
                is EnsureUserExistsInProjectFailure.Other -> it.respond(HttpStatusCode.InternalServerError, "An unknown error occurred")
            }
        }
    }
}