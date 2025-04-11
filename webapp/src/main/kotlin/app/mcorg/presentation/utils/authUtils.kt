package app.mcorg.presentation.utils

import app.mcorg.domain.model.users.User
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.date.*

const val tokenName = "MCORG-USER-TOKEN"

fun ApplicationCall.storeUser(user: User) = attributes.put(AttributeKey("user"), user)
fun ApplicationCall.getUser() = attributes[AttributeKey<User>("user")]
fun ApplicationCall.getUserId() = getUser().id

fun ResponseCookies.removeToken(host: String) {
    append(tokenName, "", expires = GMTDate(-1), httpOnly = true, domain = host, path = "/")
}

