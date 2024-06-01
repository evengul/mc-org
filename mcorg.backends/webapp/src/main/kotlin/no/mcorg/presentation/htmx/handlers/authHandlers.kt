package no.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.mcorg.presentation.configuration.usersApi
import no.mcorg.presentation.htmx.routing.getUserId
import no.mcorg.presentation.htmx.routing.getUserIdOrRedirect
import no.mcorg.presentation.htmx.routing.respondHtml
import no.mcorg.presentation.htmx.templates.pages.signinPage
import no.mcorg.presentation.security.createSignedJwtToken

suspend fun ApplicationCall.handlePostRegister() {
    val (username, password) = getAuthFormItems() ?: return

    val userId = usersApi()
        .createUser(username, password)

    signIn(createSignedJwtToken(usersApi().getUser(userId)!!))

    respondRedirect("/signin")
}

suspend fun ApplicationCall.respondSignIn() {
    val userId = getUserId()
    if (userId == null || !usersApi().userExists(userId)) {
        signOut()
        respondHtml(signinPage())
    } else {
        respondRedirect("/")
    }
}

suspend fun ApplicationCall.handlePostSignin() {
    val (username, password) = getAuthFormItems() ?: return

    val user = usersApi()
        .getUserByUsernameIfPasswordMatches(username, password)
    if (user == null) {
        respond(HttpStatusCode.Unauthorized)
    } else {
        signIn(createSignedJwtToken(user))
        respondRedirect("/")
    }
}

suspend fun ApplicationCall.respondSignOut() {
    signOut()
    respondRedirect("/")
}

private suspend fun ApplicationCall.getAuthFormItems(): Pair<String, String>? {
    val data = receiveMultipart().readAllParts()

    val username = data.find { it.name == "username" } as PartData.FormItem?
    val password = data.find { it.name == "password" } as PartData.FormItem?

    if (username == null || password == null) {
        respond(HttpStatusCode.BadRequest)
        return null
    }

    return Pair(username.value, password.value)
}