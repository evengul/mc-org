package app.mcorg.presentation.utils

import io.ktor.server.application.*
import io.ktor.util.*

fun ApplicationCall.setWorldId(id: Int) = setInt("WorldParam", id)
fun ApplicationCall.getWorldId(): Int = getInt("WorldParam")

fun ApplicationCall.setProjectId(id: Int) = setInt("ProjectParam", id)
fun ApplicationCall.getProjectId(): Int = getInt("ProjectParam")

fun ApplicationCall.setTaskId(id: Int) = setInt("TaskParam", id)
fun ApplicationCall.getTaskId(): Int = getInt("TaskParam")

fun ApplicationCall.setNotificationId(id: Int) = setInt("NotificationParam", id)
fun ApplicationCall.getNotificationId(): Int = getInt("NotificationParam")

private fun ApplicationCall.setInt(key: String, value: Int) = attributes.put(AttributeKey(key), value)
private fun ApplicationCall.getInt(key: String): Int = attributes[AttributeKey(key)]