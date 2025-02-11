package app.mcorg.domain.projects

import app.mcorg.domain.users.User

data class Task(val id: Int,
                var name: String,
                val priority: Priority,
                val dependencies: MutableList<ProjectDependency>,
                var needed: Int,
                var done: Int,
                val assignee: User?,
                val taskType: TaskType,
) {
    fun isDone(): Boolean {
        return done >= needed
    }

    fun isCountable() = taskType == TaskType.COUNTABLE
}