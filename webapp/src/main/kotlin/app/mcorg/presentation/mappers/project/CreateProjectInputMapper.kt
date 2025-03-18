package app.mcorg.presentation.mappers.project

import app.mcorg.presentation.entities.project.CreateProjectRequest
import app.mcorg.presentation.mappers.*
import io.ktor.http.*

fun InputMappers.Companion.createProjectInputMapper(parameters: Parameters): CreateProjectRequest {
    val name = parameters.required("projectName")
    val dimension = EnumMappers.mapDimension(parameters.optional("dimension"))
    val priority = EnumMappers.mapPriority(parameters.optional("priority"))
    val requiresPerimeter = parameters.optionalBoolean("requiresPerimeter") ?: false

    return CreateProjectRequest(name, priority, dimension, requiresPerimeter)
}