package app.mcorg.presentation.mappers.task

import app.mcorg.presentation.entities.task.EditCountableRequest
import app.mcorg.presentation.mappers.InputMappers
import app.mcorg.presentation.mappers.requiredInt
import io.ktor.http.*

fun InputMappers.Companion.editCountableTaskRequirementsInputMapper(parameters: Parameters): EditCountableRequest {
    val id = parameters.requiredInt("id")
    val done = parameters.requiredInt("done")
    val needed = parameters.requiredInt("needed")

    if (done > needed) throw IllegalArgumentException("You cannot do more than you need")

    return EditCountableRequest(id = id, needed = needed, done = done)
}