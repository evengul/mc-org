package app.mcorg.domain.model.task

data class ActionTask(
    val id: Int,
    val projectId: Int,
    val name: String,
    val completed: Boolean,
)
