package app.mcorg.presentation.entities.world

import java.time.ZonedDateTime

data class SlimWorld(
    val id: Int,
    val name: String,
    val description: String,
    val version: String,
    val completedProjects: Int,
    val totalProjects: Int,
    val createdAt: ZonedDateTime
)