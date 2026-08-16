package app.mcorg.presentation.utils

import app.mcorg.config.AppConfig
import app.mcorg.domain.Local
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.consts.AUTH_COOKIE
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.date.*

fun ApplicationCall.storeUser(user: TokenProfile) = attributes.put(AttributeKey("user"), user)
fun ApplicationCall.getUser() = attributes[AttributeKey<TokenProfile>("user")]

fun ResponseCookies.removeToken(host: String) {
    // Match the attributes set on the live cookie so the deletion cookie is accepted and the
    // session is reliably cleared — including Secure, which AddCookieStep now sets everywhere
    // except Local (MCO-356). Keep these two in step: a deletion whose attributes do not match
    // is silently ignored, and the symptom is "sign-out does nothing".
    val secure = AppConfig.env != Local
    val sameSite = mapOf("SameSite" to "Lax")
    if (host == "false") {
        append(AUTH_COOKIE, "", expires = GMTDate(-1), maxAge = 0, httpOnly = true, path = "/", secure = secure, extensions = sameSite)
    } else {
        append(AUTH_COOKIE, "", expires = GMTDate(-1), maxAge = 0, httpOnly = true, domain = host, path = "/", secure = secure, extensions = sameSite)
    }
}

