package app.mcorg.presentation.entities.project

import app.mcorg.domain.minecraft.Dimension
import app.mcorg.domain.projects.Priority

data class CreateProjectRequest(
    val name: String,
    val priority: Priority,
    val dimension: Dimension,
    val requiresPerimeter: Boolean
)