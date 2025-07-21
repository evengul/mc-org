package app.mcorg.domain.model.task

import app.mcorg.domain.model.v2.project.ProjectStage
import app.mcorg.domain.model.v2.task.TaskRequirement

data class Task(
    val id: Int,
    val projectId: Int,
    val name: String,
    val description: String,
    val stage: ProjectStage,
    val priority: Priority,
    val requirements: List<TaskRequirement>
)
