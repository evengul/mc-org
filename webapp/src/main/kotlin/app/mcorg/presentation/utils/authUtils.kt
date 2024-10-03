package app.mcorg.presentation.utils

import app.mcorg.domain.User
import app.mcorg.presentation.security.getUserFromJwtToken
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.date.*
import java.time.Instant

const val tokenName = "MCORG-USER-TOKEN"

fun ApplicationCall.storeUser(user: User) = attributes.put(AttributeKey("user"), user)
fun ApplicationCall.getUser() = attributes[AttributeKey<User>("user")]
fun ApplicationCall.getUserId() = getUser().id

fun ApplicationCall.getUserFromCookie(): User? = request.cookies[tokenName]?.let { getUserFromJwtToken(it) }

fun ApplicationCall.addToken(token: String) = response.cookies.append(tokenName, expires = GMTDate(timestamp = Instant.now().plusSeconds(8 * 60 * 60).toEpochMilli()), value = token, httpOnly = true, domain = getCookieHost(), path = "/")

suspend fun ApplicationCall.removeTokenAndSignOut() {
    response.cookies.append(tokenName, "", expires = GMTDate(-1), httpOnly = true, domain = getCookieHost(), path = "/")

    respondRedirect("/auth/sign-in", permanent = false)
}

