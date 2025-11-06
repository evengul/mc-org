package app.mcorg.pipeline.world.extractors

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import java.sql.ResultSet

fun ResultSet.toWorlds() = buildList {
    while (next()) {
        add(toWorld())
    }
}

fun ResultSet.toWorld() = World(
    id = getInt("id"),
    name = getString("name"),
    description = getString("description") ?: "",
    version = MinecraftVersion.fromString(getString("version")),
    completedProjects = getInt("completed_projects"),
    totalProjects = getInt("total_projects"),
    createdAt = getTimestamp("created_at").toInstant().atZone(java.time.ZoneOffset.UTC),
    updatedAt = getTimestamp("updated_at").toInstant().atZone(java.time.ZoneOffset.UTC)
)