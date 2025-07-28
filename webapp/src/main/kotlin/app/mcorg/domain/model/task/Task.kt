package app.mcorg.domain.model.task

import app.mcorg.domain.model.project.ProjectStage

data class Task(
    val id: Int,
    val projectId: Int,
    val name: String,
    val description: String,
    val stage: ProjectStage,
    val priority: Priority,
    val requirements: List<TaskRequirement>
) {
    fun progress(): Double {
        if (requirements.isEmpty()) return 0.0
        val completed = requirements.count { it.isCompleted() }
        return (completed.toDouble() / requirements.size) * 100.0
    }
}
