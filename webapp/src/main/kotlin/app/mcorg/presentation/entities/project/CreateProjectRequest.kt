package app.mcorg.presentation.entities.project

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.projects.Priority

data class CreateProjectRequest(
    val name: String,
    val priority: Priority,
    val dimension: Dimension,
    val requiresPerimeter: Boolean
)