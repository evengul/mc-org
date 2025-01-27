package app.mcorg.presentation.handler

import app.mcorg.domain.User
import app.mcorg.presentation.security.createSignedJwtToken
import app.mcorg.presentation.configuration.minecraftApi
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.configuration.usersApi
import app.mcorg.presentation.security.JwtHelper
import app.mcorg.presentation.templates.auth.signInTemplate
import app.mcorg.presentation.utils.*
import com.auth0.jwt.exceptions.TokenExpiredException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetSignIn() {
    val user = try {
        getUserFromCookie().takeUnless { it == null || usersApi.getUser(it.id) == null }
    } catch (e: TokenExpiredException) {
        null
    }

    if (user == null) {
        if (getSkipMicrosoftSignIn() == "true") {
            respondHtml(signInTemplate("/auth/oidc/local-redirect"))
        } else {
            respondHtml(signInTemplate(getMicrosoftSignInUrl()))
        }
    } else {
        val profile = usersApi.getProfile(user.id) ?: return removeTokenAndSignOut()
        if (profile.selectedWorld != null) {
            respondRedirect("/app/worlds/${profile.selectedWorld}/projects")
            return
        }
        respondRedirect("/app/worlds/add")
    }
}

suspend fun ApplicationCall.handleLocalSignIn() {
    if(getEnvironment() == "LOCAL" || getSkipMicrosoftSignIn() == "true") {
        addToken(JwtHelper.createSignedJwtToken(getLocalUser()))
        respondRedirect("/")
    } else {
        respond(HttpStatusCode.Forbidden)
    }
}

private fun getLocalUser(): User {
    val user = usersApi.getUser("evegul")
    if (user == null) {
        val userId = usersApi.createUser("evegul", "even.gultvedt@gmail.com")
        return usersApi.getUser(userId)!!
    }
    return user
}

suspend fun ApplicationCall.handleSignIn() {
    val code = parameters["code"] ?: return respondHtml("Some error occurred")
    val clientId = getMicrosoftClientId()
    val clientSecret = getMicrosoftClientSecret()
    val host = getCookieHost()

    val profile = minecraftApi.getProfile(code, clientId, clientSecret, host)

    val user = usersApi.getUser(profile.username) ?: usersApi.getUser(usersApi.createUser(profile.username, profile.email)) ?: return respondHtml("Some error occurred")

    val token = JwtHelper.createSignedJwtToken(user)
    addToken(token)

    respondRedirect("/")
}

suspend fun ApplicationCall.handleGetSignOut() = removeTokenAndSignOut()

suspend fun ApplicationCall.handleDeleteUser() {
    val userId = getUserFromCookie()?.id ?: return removeTokenAndSignOut()

    projectsApi.removeUserAssignments(userId)
    permissionsApi.removeUserPermissions(userId)
    usersApi.deleteUser(userId)

    removeTokenAndSignOut()
}

private fun ApplicationCall.getMicrosoftSignInUrl(): String {
    val clientId = getMicrosoftClientId()
    val host = getCookieHost()
    val redirectUrl =
        if (host == "localhost") "http://localhost:8080/auth/oidc/microsoft-redirect"
        else "https://mcorg.app/auth/oidc/microsoft-redirect"
    return "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?response_type=code&scope=openid,XboxLive.signin&client_id=$clientId&redirect_uri=$redirectUrl"
}
