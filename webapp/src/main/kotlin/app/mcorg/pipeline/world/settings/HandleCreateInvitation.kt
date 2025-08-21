package app.mcorg.pipeline.world.settings

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.handleCreateInvitation() {
    respond(HttpStatusCode.NotImplemented)
}