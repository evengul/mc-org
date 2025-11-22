package app.mcorg.domain.model.project

import app.mcorg.domain.model.minecraft.MinecraftLocation
import java.time.ZonedDateTime

data class Project(
    val id: Int,
    val worldId: Int,
    val name: String,
    val description: String,
    val type: ProjectType,
    val stage: ProjectStage,
    val location: MinecraftLocation?,
    val tasksTotal: Int,
    val tasksCompleted: Int,
    val importedFromIdea: Pair<Int, String>? = null,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)
