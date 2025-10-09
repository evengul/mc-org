package app.mcorg.pipeline.world

import app.mcorg.pipeline.failure.HandleGetWorldFailure
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.handleWorldFailure(worldFailure: HandleGetWorldFailure) {
    when (worldFailure) {
        HandleGetWorldFailure.WorldIdRequired -> respond(HttpStatusCode.BadRequest, "World ID is required")
        HandleGetWorldFailure.InvalidWorldId -> respond(HttpStatusCode.UnprocessableEntity, "Invalid World ID format")
        HandleGetWorldFailure.WorldNotFound -> respond(HttpStatusCode.NotFound, "World not found")
        is HandleGetWorldFailure.SystemError -> respond(HttpStatusCode.InternalServerError,worldFailure.message)
    }
}