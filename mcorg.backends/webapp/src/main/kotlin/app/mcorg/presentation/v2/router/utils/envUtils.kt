package app.mcorg.presentation.v2.router.utils

import io.ktor.server.application.*
import io.ktor.util.*

fun ApplicationCall.setDBUrl(url: String) = setAttribute("DB_URL", url)
fun ApplicationCall.getDBUrl() = getAttribute<String>("DB_URL")

fun ApplicationCall.setDBUser(url: String) = setAttribute("DB_USER", url)
fun ApplicationCall.getDBUser() = getAttribute<String>("DB_USER")

fun ApplicationCall.setDBPassword(url: String) = setAttribute("DB_PASSWORD", url)
fun ApplicationCall.getDBPassword() = getAttribute<String>("DB_PASSWORD")

fun ApplicationCall.setMicrosoftClientId(clientId: String) = setAttribute("MICROSOFT_CLIENT_ID", clientId)
fun ApplicationCall.getMicrosoftClientId() = getAttribute<String>("MICROSOFT_CLIENT_ID")

fun ApplicationCall.setMicrosoftClientSecret(secret: String) = setAttribute("MICROSOFT_CLIENT_SECRET", secret)
fun ApplicationCall.getMicrosoftClientSecret() = getAttribute<String>("MICROSOFT_CLIENT_SECRET")

private fun <T : Any> ApplicationCall.getAttribute(key: String): T = attributes[AttributeKey(key)]
private fun <T : Any> ApplicationCall.setAttribute(key: String, value: T) = attributes.put(AttributeKey(key), value)