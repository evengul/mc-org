package app.mcorg.domain.model.projects

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.users.User

data class SlimProject(val worldId: Int,
                       val id: Int,
                       val name: String,
                       val priority: Priority,
                       val dimension: Dimension,
                       val assignee: User?,
                       val progress: Double)