package app.mcorg.domain.model.task

data class Task(
    val id: Int,
    val projectId: Int,
    val name: String,
    val description: String,
    val stage: TaskProjectStage,
    val priority: Priority,
    val requirement: TaskRequirement,
    val solvedByProject: Pair<Int, String>? = null
) {
    fun isCompleted() = requirement.isCompleted()
}

