package app.mcorg.presentation.v2.handler

import app.mcorg.presentation.security.createSignedJwtToken
import app.mcorg.presentation.v2.configuration.minecraftApi
import app.mcorg.presentation.v2.configuration.usersApi
import app.mcorg.presentation.v2.router.utils.*
import app.mcorg.presentation.v2.templates.auth.signInTemplate
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetSignIn() {
    val user = getUserFromCookie()

    if (user == null) {
        respondHtml(signInTemplate(getMicrosoftSignInUrl()))
    } else {
        val profile = usersApi.getProfile(user.id) ?: return removeTokenAndSignOut()
        if (profile.selectedWorld != null) {
            respondRedirect("/app/worlds/${profile.selectedWorld}/projects")
            return
        }
        respondRedirect("/app/worlds/add")
    }
}

suspend fun ApplicationCall.handleSignIn() {
    val code = parameters["code"] ?: return respondHtml("Some error occurred")
    val clientId = getMicrosoftClientId()
    val clientSecret = getMicrosoftClientSecret()

    val profile = minecraftApi.getProfile(code, clientId, clientSecret)

    val user = usersApi.getUser(profile.username) ?: usersApi.getUser(usersApi.createUser(profile.username, profile.email)) ?: return respondHtml("Some error occurred")

    val token = createSignedJwtToken(user)
    addToken(token)

    respondRedirect("/")
}

suspend fun ApplicationCall.handleGetSignOut() = removeTokenAndSignOut()

suspend fun ApplicationCall.handleDeleteUser() {
    val userId = getUserFromCookie()?.id ?: return removeTokenAndSignOut()

    usersApi.deleteUser(userId)

    removeTokenAndSignOut()
}

private fun ApplicationCall.getMicrosoftSignInUrl(): String {
    val clientId = getMicrosoftClientId()
    return "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?response_type=code&scope=openid,XboxLive.signin&client_id=$clientId"
}
