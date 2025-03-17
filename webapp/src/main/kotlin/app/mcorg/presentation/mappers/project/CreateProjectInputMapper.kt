package app.mcorg.presentation.mappers.project

import app.mcorg.domain.minecraft.Dimension
import app.mcorg.domain.projects.Priority
import app.mcorg.presentation.entities.project.CreateProjectRequest
import app.mcorg.presentation.mappers.*
import io.ktor.http.*

fun InputMappers.Companion.createProjectInputMapper(parameters: Parameters): CreateProjectRequest {
    val name = parameters.required("projectName")
    val dimension = parameters.optional("dimension")?.toDimension() ?: Dimension.OVERWORLD
    val priority = parameters.optional("priority")?.toPriority() ?: Priority.NONE
    val requiresPerimeter = parameters.optionalBoolean("requiresPerimeter") ?: false

    return CreateProjectRequest(name, priority, dimension, requiresPerimeter)
}