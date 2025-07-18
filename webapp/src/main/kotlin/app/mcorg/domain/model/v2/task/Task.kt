package app.mcorg.domain.model.v2.task

import app.mcorg.domain.model.projects.Priority
import app.mcorg.domain.model.v2.project.ProjectStage

data class Task(
    val id: Int,
    val projectId: Int,
    val name: String,
    val description: String,
    val stage: ProjectStage,
    val priority: Priority,
    val requirements: List<TaskRequirement>
)
