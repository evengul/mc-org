package app.mcorg.presentation.utils

import io.ktor.server.application.*
import io.ktor.util.*

fun ApplicationCall.setMicrosoftClientId(clientId: String) = setAttribute("MICROSOFT_CLIENT_ID", clientId)
fun ApplicationCall.getMicrosoftClientId() = getAttribute<String>("MICROSOFT_CLIENT_ID")

fun ApplicationCall.setMicrosoftClientSecret(secret: String) = setAttribute("MICROSOFT_CLIENT_SECRET", secret)
fun ApplicationCall.getMicrosoftClientSecret() = getAttribute<String>("MICROSOFT_CLIENT_SECRET")

fun ApplicationCall.setEnvironment(environment: String) = setAttribute("ENVIRONMENT", environment)
fun ApplicationCall.getEnvironment() = getAttribute<String>("ENVIRONMENT")

fun ApplicationCall.setCookieHost(cookieHost: String) = setAttribute("COOKIE_HOST", cookieHost)
fun ApplicationCall.getCookieHost() = getAttribute<String>("COOKIE_HOST")

fun ApplicationCall.setSkipMicrosoftSignIn(cookieHost: String) = setAttribute("SKIP_MICROSOFT_SIGN_IN", cookieHost)
fun ApplicationCall.getSkipMicrosoftSignIn() = getAttribute<String>("SKIP_MICROSOFT_SIGN_IN")

private inline fun <reified T : Any> ApplicationCall.getAttribute(key: String): T = attributes[AttributeKey(key)]
private inline fun <reified T : Any> ApplicationCall.setAttribute(key: String, value: T) = attributes.put(AttributeKey(key), value)