package app.mcorg.presentation.mappers.world

import app.mcorg.presentation.entities.world.CreateWorldRequest
import app.mcorg.presentation.mappers.InputMappers
import app.mcorg.presentation.mappers.required
import io.ktor.http.*

fun InputMappers.Companion.createWorldInputMapper(parameters: Parameters): CreateWorldRequest {
    val worldName = parameters.required("worldName")
    if (worldName.length < 3) throw IllegalArgumentException("worldName must be longer than 3 characters")
    return CreateWorldRequest(worldName)
}