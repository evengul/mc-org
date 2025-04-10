package app.mcorg.domain.model.task

import app.mcorg.domain.model.projects.Priority
import app.mcorg.domain.model.projects.ProjectDependency
import app.mcorg.domain.model.users.User

data class Task(val id: Int,
                var name: String,
                val priority: Priority,
                val dependencies: MutableList<ProjectDependency>,
                var needed: Int,
                var done: Int,
                val assignee: User?,
                val taskType: TaskType,
                val stage: TaskStage,
) {
    fun isDone(): Boolean {
        return done >= needed || stage == TaskStages.DONE
    }

    fun isCountable() = taskType == TaskType.COUNTABLE
}