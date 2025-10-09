package app.mcorg.domain.model.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import java.time.ZonedDateTime

data class World(
    val id: Int,
    val name: String,
    val description: String,
    val version: MinecraftVersion,
    val completedProjects: Int,
    val totalProjects: Int,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)
