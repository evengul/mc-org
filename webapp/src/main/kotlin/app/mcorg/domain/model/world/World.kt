package app.mcorg.domain.model.v2.world

import app.mcorg.domain.model.v2.minecraft.MinecraftVersion
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
