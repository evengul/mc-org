package app.mcorg.domain.model.v2.project

import app.mcorg.domain.model.v2.minecraft.MinecraftLocation
import java.time.ZonedDateTime

data class Project(
    val id: Int,
    val name: String,
    val description: String,
    val type: ProjectType,
    val stage: ProjectStage,
    val location: MinecraftLocation,
    val tasksTotal: Int,
    val tasksCompleted: Int,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
    val stageChanges: Map<ProjectStage, ZonedDateTime>,
    val dependencies: List<ProjectDependency>
)
