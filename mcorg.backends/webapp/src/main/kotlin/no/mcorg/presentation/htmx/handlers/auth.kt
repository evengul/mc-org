package no.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.date.*
import no.mcorg.presentation.configuration.usersApi
import no.mcorg.presentation.htmx.routing.isHtml
import no.mcorg.presentation.htmx.templates.pages.registerPage
import no.mcorg.presentation.htmx.templates.pages.signinPage

suspend fun ApplicationCall.handleGetRegister() {
    isHtml()
    respond(registerPage())
}

suspend fun ApplicationCall.handlePostRegister() {
    val data = receiveMultipart().readAllParts()

    val username = data.find { it.name == "username" } as PartData.FormItem?
    val password = data.find { it.name == "password" } as PartData.FormItem?

    if (username == null || password == null) {
        respond(HttpStatusCode.BadRequest)
    } else {
        val userId = usersApi()
            .createUser(username.value, password.value)
        response.cookies.append("MCORG-USER-ID", userId.toString())
        respondRedirect("/signin")
    }
}

suspend fun ApplicationCall.handleGetSignin() {
    val userId = request.cookies["MCORG-USER-ID"]?.toIntOrNull()
    if (userId != null && usersApi().userExists(userId)) {
        respondRedirect("/")
    } else {
        response.cookies.append("MCORG-USER-ID", "", expires = GMTDate(-1))
        isHtml()
        respond(signinPage())
    }
}

suspend fun ApplicationCall.handlePostSignin() {
    val data = receiveMultipart().readAllParts()

    val username = data.find { it.name == "username" } as PartData.FormItem?
    val password = data.find { it.name == "password" } as PartData.FormItem?

    if (username == null || password == null) {
        respond(HttpStatusCode.BadRequest)
    } else {
        val user = usersApi()
            .getUserByUsernameIfPasswordMatches(username.value, password.value)
        if (user == null) {
            respond(HttpStatusCode.Unauthorized)
        } else {
            response.cookies.append("MCORG-USER-ID", user.id.toString())
            respondRedirect("/")
        }
    }
}

suspend fun ApplicationCall.handleGetSignout() {
    response.cookies.append("MCORG-USER-ID", "", expires = GMTDate(-1))
    respondRedirect("/")
}