package app.mcorg.domain.model.admin

import app.mcorg.domain.model.minecraft.MinecraftVersion
import java.time.ZonedDateTime

data class ManagedWorld(
    val id: Int,
    val name: String,
    val version: MinecraftVersion,
    val projects: Int,
    val members: Int,
    val createdAt: ZonedDateTime,
)