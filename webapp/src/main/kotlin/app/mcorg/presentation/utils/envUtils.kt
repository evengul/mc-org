package app.mcorg.presentation.utils

import app.mcorg.model.Local
import app.mcorg.model.Prod
import app.mcorg.model.Test
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.request.host
import io.ktor.util.*

fun ApplicationCall.setMicrosoftClientId(clientId: String) = setAttribute("MICROSOFT_CLIENT_ID", clientId)
fun ApplicationCall.getMicrosoftClientId() = getAttribute<String>("MICROSOFT_CLIENT_ID")

fun ApplicationCall.setMicrosoftClientSecret(secret: String) = setAttribute("MICROSOFT_CLIENT_SECRET", secret)
fun ApplicationCall.getMicrosoftClientSecret() = getAttribute<String>("MICROSOFT_CLIENT_SECRET")

fun ApplicationCall.setEnvironment(environment: String) = setAttribute("ENVIRONMENT", environment)
fun ApplicationCall.getEnvironment() = when(getAttribute<String>("ENVIRONMENT")) {
    "PRODUCTION" -> Prod
    "TEST" -> Test
    "LOCAL" -> Local
    else -> throw IllegalStateException("Invalid ENV=[$this]")
}

fun ApplicationCall.getHost(): String? {
    val env = getEnvironment()
    val referrer = request.headers[HttpHeaders.Referrer] ?: request.host()
    return when (env) {
        Prod -> return if (referrer.contains("mcorg.fly.dev")) "mcorg.fly.dev"
                        else "mcorg.app"
        Test -> System.getenv("TEST_HOST") ?: "http://localhost:8080"
        Local -> null
        else -> throw IllegalStateException("Invalid ENV=[$env]")
    }
}

fun ApplicationCall.setSkipMicrosoftSignIn(cookieHost: String) = setAttribute("SKIP_MICROSOFT_SIGN_IN", cookieHost)
fun ApplicationCall.getSkipMicrosoftSignIn() = getAttribute<String>("SKIP_MICROSOFT_SIGN_IN")

private inline fun <reified T : Any> ApplicationCall.getAttribute(key: String): T = attributes[AttributeKey(key)]
private inline fun <reified T : Any> ApplicationCall.setAttribute(key: String, value: T) = attributes.put(AttributeKey(key), value)