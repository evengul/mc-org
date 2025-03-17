package app.mcorg.presentation.mappers.project

import app.mcorg.presentation.entities.project.ProjectFiltersRequest
import app.mcorg.presentation.mappers.InputMappers
import io.ktor.http.*

fun InputMappers.Companion.projectFilterInputMapper(parameters: Parameters) = ProjectFiltersRequest(
    parameters["search"],
    parameters["hideCompleted"] == "on"
)