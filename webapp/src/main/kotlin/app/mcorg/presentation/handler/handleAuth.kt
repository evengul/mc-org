package app.mcorg.presentation.handler

import app.mcorg.domain.users.User
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
import kotlin.random.Random

suspend fun ApplicationCall.handleGetSignIn() {
    val user = try {
        getUserFromCookie().takeUnless { it == null || usersApi.getUser(it.id) == null }
    } catch (_: TokenExpiredException) {
        null
    }

    if (user == null) {
        val url = when {
            getSkipMicrosoftSignIn().lowercase() != "true" -> getMicrosoftSignInUrl()
            getEnvironment() == "TEST" -> "/auth/oidc/test-redirect"
            else -> "/auth/oidc/local-redirect"
        }
        respondHtml(signInTemplate(url))
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
    if(getEnvironment() == "LOCAL" || getSkipMicrosoftSignIn().lowercase() == "true") {
        addToken(JwtHelper.createSignedJwtToken(getLocalUser(), getJwtIssuer()))
        respondRedirect("/")
    } else {
        respond(HttpStatusCode.Forbidden)
    }
}

suspend fun ApplicationCall.handleTestSignIn() {
    if (getEnvironment() == "TEST") {
        addToken(JwtHelper.createSignedJwtToken(getTestUser(), getJwtIssuer()))
        respondRedirect("/")
    } else {
        respond(HttpStatusCode.Forbidden)
    }
}

private fun getTestUser(): User {
    val username = "TestUser_${Random.nextInt(100_000)}"
    val userId = usersApi.createUser(username, "test-$username@mcorg.app")
    return usersApi.getUser(userId)!!
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
    val env = getEnvironment()
    val host = getHost()

    val profile = minecraftApi.getProfile(code, clientId, clientSecret, env, host)

    val user = usersApi.getUser(profile.username) ?: usersApi.getUser(usersApi.createUser(profile.username, profile.email)) ?: return respondHtml("Some error occurred")

    val token = JwtHelper.createSignedJwtToken(user, getJwtIssuer())
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
    val env = getEnvironment()
    val host = getHost()
    val redirectUrl =
        if (env == "LOCAL") "http://localhost:8080/auth/oidc/microsoft-redirect"
        else "https://$host/auth/oidc/microsoft-redirect"
    return "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?response_type=code&scope=openid,XboxLive.signin&client_id=$clientId&redirect_uri=$redirectUrl"
}
