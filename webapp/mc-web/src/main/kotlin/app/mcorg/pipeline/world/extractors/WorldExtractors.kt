package app.mcorg.pipeline.world.extractors

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import app.mcorg.domain.model.world.WorldProjectTally
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
    updatedAt = getTimestamp("updated_at").toInstant().atZone(java.time.ZoneOffset.UTC),
    farmScaleThreshold = getInt("farm_scale_threshold"),
    preferredWoodSpecies = getString("preferred_wood_species"),
    projectTally = toProjectTally(),
)

/**
 * Reads the per-state project counts every world query selects via
 * [app.mcorg.pipeline.world.commonsteps.projectTallyColumns].
 */
fun ResultSet.toProjectTally() = WorldProjectTally(
    active = getInt("active_projects"),
    pending = getInt("pending_projects"),
    paused = getInt("paused_projects"),
    done = getInt("done_projects"),
)