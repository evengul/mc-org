package app.mcorg.pipeline.project.extractors

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.minecraft.MinecraftLocation
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectType
import java.sql.ResultSet
import java.time.ZoneOffset

fun ResultSet.toProjects() = buildList {
    while (next()) {
        add(toProject())
    }
}

fun ResultSet.toProject() = Project(
    id = getInt("id"),
    worldId = getInt("world_id"),
    name = getString("name"),
    description = getString("description") ?: "",
    type = ProjectType.valueOf(getString("type")),
    stage = ProjectStage.valueOf(getString("stage")),
    location = MinecraftLocation(
        dimension = Dimension.valueOf(getString("location_dimension")),
        x = getInt("location_x"),
        y = getInt("location_y"),
        z = getInt("location_z")
    ),
    tasksTotal = getInt("tasks_total"),
    tasksCompleted = getInt("tasks_completed"),
    importedFromIdea = run {
        val ideaId = getInt("project_idea_id").takeIf { !wasNull() }
        val ideaName = getString("idea_name")
        if (ideaId != null && ideaName != null) {
            Pair(ideaId, ideaName)
        } else null
    },
    createdAt = getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC),
    updatedAt = getTimestamp("updated_at").toInstant().atZone(ZoneOffset.UTC)
)