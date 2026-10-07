package app.mcorg.presentation.handler

import app.mcorg.pipeline.failure.SignOutReason
import app.mcorg.presentation.templated.error.signOutPage
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.removeToken
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * Signs the caller out, on every branch. `?error=` is how the rest of the app says *why* — an
 * invalid token, a failed Microsoft sign-in — and the case that most needs the cookie gone is a bad
 * token, so the error branch clears it too (MCO-438).
 *
 * Nothing from the URL is printed: the code only selects copy from [SignOutReason], so a crafted link
 * cannot put its own words on this page.
 */
suspend fun ApplicationCall.handleGetSignOut() {
    response.cookies.removeToken(getHost() ?: "false")

    val errorCode = request.queryParameters["error"]
    if (errorCode == null) {
        respondRedirect("/auth/sign-in", permanent = false)
        return
    }

    respondHtml(signOutPage(SignOutReason.fromCode(errorCode)))
}
