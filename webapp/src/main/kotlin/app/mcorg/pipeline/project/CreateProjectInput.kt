package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectType

data class CreateProjectInput(
    val name: String,
    val description: String,
    val type: ProjectType,
)
