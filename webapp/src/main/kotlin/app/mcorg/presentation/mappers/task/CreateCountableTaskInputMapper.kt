package app.mcorg.presentation.mappers.task

import app.mcorg.presentation.entities.task.AddCountableRequest
import app.mcorg.presentation.mappers.InputMappers
import app.mcorg.presentation.mappers.required
import app.mcorg.presentation.mappers.requiredInt
import io.ktor.http.*

fun InputMappers.Companion.createCountableTaskInputMapper(parameters: Parameters): AddCountableRequest {
    val name = parameters.required("taskName")
    val amount = parameters.requiredInt("amount")
    return AddCountableRequest(name, amount)
}