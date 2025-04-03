package app.mcorg.presentation.plugins

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.auth.AuthPluginFailure
import app.mcorg.pipeline.auth.ConvertTokenStep
import app.mcorg.pipeline.auth.GetTokenStep
import app.mcorg.pipeline.auth.toRedirect
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import io.ktor.server.request.RequestCookies
import io.ktor.server.response.respondRedirect


val AuthPlugin = createRouteScopedPlugin("AuthPlugin") {
    onCall {
        val result = Pipeline.create<AuthPluginFailure, RequestCookies>()
            .pipe(GetTokenStep(AUTH_COOKIE))
            .pipe(ConvertTokenStep(ISSUER))
            .map { user -> it.storeUser(user) }
            .execute(it.request.cookies)
        if (result is Result.Failure) {
            it.response.cookies.removeToken(it.getHost() ?: "localhost")
            val url = result.error.toRedirect("/auth/sign-in").url
            it.respondRedirect(url, permanent = false)
        }
    }
}

val WorldParticipantPlugin = createRouteScopedPlugin("WorldAccessPlugin") {
    onCall {
        val userId = it.getUserId()
        val worldId = it.getWorldId()
        val hasAccess = permissionsApi.hasWorldPermission(userId, Authority.PARTICIPANT, worldId)
        if (!hasAccess) {
            throw IllegalCallerException("User does not have access to world")
        }
    }
}