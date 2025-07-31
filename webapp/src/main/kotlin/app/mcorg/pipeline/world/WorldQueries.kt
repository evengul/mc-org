package app.mcorg.pipeline.world

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.minecraft.MinecraftLocation
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.SafeSQL
import java.sql.ResultSet

val getWorldQuery = SafeSQL.select("""
    SELECT 
        world.id, 
        world.name, 
        world.description, 
        world.version, 
        world.created_at, 
        world.updated_at,
        COALESCE(COUNT(projects.id), 0) as total_projects,
        COALESCE(SUM(CASE WHEN projects.stage = 'COMPLETED' THEN 1 ELSE 0 END), 0) as completed_projects
    FROM world 
    LEFT JOIN projects ON world.id = projects.world_id
    WHERE world.id = ?
    group by world.id 
""".trimIndent())

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

fun ResultSet.toProjects() = buildList {
    while (next()) {
        add(Project(
            id = getInt("id"),
            worldId = getInt("world_id"),
            name = getString("name"),
            description = getString("description") ?: "",
            type = ProjectType.valueOf(getString("type")),
            stage = ProjectStage.valueOf(getString("stage")),
            stageProgress = getDouble("stage_progress"),
            location = MinecraftLocation(
                dimension = Dimension.valueOf(getString("location_dimension")),
                x = getInt("location_x"),
                y = getInt("location_y"),
                z = getInt("location_z")
            ),
            tasksTotal = getInt("tasks_total"),
            tasksCompleted = getInt("tasks_completed"),
            createdAt = getTimestamp("created_at").toInstant().atZone(java.time.ZoneOffset.UTC),
            updatedAt = getTimestamp("updated_at").toInstant().atZone(java.time.ZoneOffset.UTC)
        ))
    }
}

val hasWorldRoleOrHigherQuery = SafeSQL.select("""
    SELECT EXISTS (
        SELECT 1 
        FROM world_members 
        WHERE user_id = ? AND world_id = ? AND world_role <= ?
    ) AS has_role
""".trimIndent())

val deleteWorldQuery = SafeSQL.delete("DELETE FROM world WHERE id = ?")