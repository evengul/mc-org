package app.mcorg.presentation.utils

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.consts.AUTH_COOKIE
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.date.*

fun ApplicationCall.storeUser(user: TokenProfile) = attributes.put(AttributeKey("user"), user)
fun ApplicationCall.getUser() = attributes[AttributeKey<TokenProfile>("user")]
fun ApplicationCall.getUserId() = getUser().id

fun ResponseCookies.removeToken(host: String) {
    if (host == "false") {
        append(AUTH_COOKIE, "", expires = GMTDate(-1), maxAge = 0, httpOnly = true, path = "/")
    } else {
        append(AUTH_COOKIE, "", expires = GMTDate(-1), maxAge = 0, httpOnly = true, domain = host, path = "/")
    }
}

