package app.mcorg.presentation.v2.router.utils

import app.mcorg.presentation.v2.entities.CreateWorldRequest
import io.ktor.server.application.*
import io.ktor.server.request.*

suspend fun ApplicationCall.receiveCreateWorldRequest(): CreateWorldRequest {
    val data = receiveParameters()
    val worldName = data["worldName"] ?: throw IllegalArgumentException("worldName is required")
    return CreateWorldRequest(worldName)
}