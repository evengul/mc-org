package app.mcorg.domain.model.projects

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.users.User

data class Project(val worldId: Int,
                   val id: Int,
                   val name: String,
                   val archived: Boolean,
                   val priority: Priority,
                   val dimension: Dimension,
                   val assignee: User?,
                   val progress: Double,
                   val requiresPerimeter: Boolean,
                   val tasks: MutableList<Task>) {
    fun doable() = tasks.filter { !it.isCountable() }
    fun countable() = tasks.filter { it.isCountable() }

    fun toSlim() = SlimProject(
        worldId = worldId,
        id = id,
        name = name,
        priority = priority,
        dimension = dimension,
        assignee = assignee,
        progress = progress
    )
}