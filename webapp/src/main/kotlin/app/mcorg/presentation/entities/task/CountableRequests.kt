package app.mcorg.presentation.entities.task

data class AddCountableRequest(val taskName: String, val needed: Int)
data class EditCountableRequest(val id: Int, val needed: Int, val done: Int)
