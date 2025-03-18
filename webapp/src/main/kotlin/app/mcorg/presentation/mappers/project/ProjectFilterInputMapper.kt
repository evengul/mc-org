package app.mcorg.presentation.mappers.project

import app.mcorg.domain.model.projects.ProjectSpecification
import app.mcorg.presentation.mappers.InputMappers
import io.ktor.http.*

fun InputMappers.Companion.projectFilterInputMapper(parameters: Parameters) = ProjectSpecification(
    parameters["search"],
    parameters["hideCompleted"] == "on"
)