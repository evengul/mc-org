package no.mcorg.presentation.htmx.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import no.mcorg.presentation.configuration.usersApi

suspend fun ApplicationCall.handleSignin() {
    val api = usersApi()
    val id = api.getUser(1)?.id ?: api.createUser("evegul", "password")
    response.cookies.append("MCORG-USER-ID", id.toString())
    respondRedirect("/")
}