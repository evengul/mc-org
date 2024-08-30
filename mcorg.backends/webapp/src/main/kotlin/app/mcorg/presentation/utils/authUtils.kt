package app.mcorg.presentation.utils

import app.mcorg.domain.User
import app.mcorg.presentation.security.getUserFromJwtToken
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.date.*

const val tokenName = "MCORG-USER-TOKEN"

fun ApplicationCall.storeUser(user: User) = attributes.put(AttributeKey("user"), user)
fun ApplicationCall.getUser() = attributes[AttributeKey<User>("user")]
fun ApplicationCall.getUserId() = getUser().id

fun ApplicationCall.getUserFromCookie(): User? = request.cookies[tokenName]?.let { getUserFromJwtToken(it) }

fun ApplicationCall.addToken(token: String) = response.cookies.append(tokenName, token, httpOnly = true, domain = "localhost", path = "/")

suspend fun ApplicationCall.removeTokenAndSignOut() {
    response.cookies.append(tokenName, "", expires = GMTDate(-1), httpOnly = true)

    respondRedirect("/auth/sign-in")
}

