package app.mcorg.presentation.mappers.task

import app.mcorg.presentation.entities.task.AddDoableRequest
import app.mcorg.presentation.mappers.InputMappers
import app.mcorg.presentation.mappers.required
import io.ktor.http.*

fun InputMappers.Companion.createDoableTaskInputMapper(parameters: Parameters): AddDoableRequest {
    val name = parameters.required("taskName")
    return AddDoableRequest(name)
}