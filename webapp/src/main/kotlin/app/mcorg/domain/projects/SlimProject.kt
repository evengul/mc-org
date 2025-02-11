package app.mcorg.domain.projects

import app.mcorg.domain.minecraft.Dimension
import app.mcorg.domain.users.User

data class SlimProject(val worldId: Int,
                       val id: Int,
                       val name: String,
                       val priority: Priority,
                       val dimension: Dimension,
                       val assignee: User?,
                       val progress: Double)