package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.minecraft.MinecraftLocation
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

sealed interface GetProjectByIdFailures {
    data object ProjectNotFound : GetProjectByIdFailures
    data object AccessDenied : GetProjectByIdFailures
    data object DatabaseError : GetProjectByIdFailures
}

object GetProjectByIdStep : Step<Int, GetProjectByIdFailures, Project> {
    override suspend fun process(input: Int): Result<GetProjectByIdFailures, Project> {
        val projectStep = DatabaseSteps.query<Int, GetProjectByIdFailures, Project?>(
            sql = SafeSQL.select("""
                SELECT 
                    p.id,
                    p.world_id,
                    p.name,
                    p.description,
                    p.type,
                    p.stage,
                    p.location_x,
                    p.location_y,
                    p.location_z,
                    p.location_dimension,
                    p.created_at,
                    p.updated_at,
                    COALESCE(task_counts.total_tasks, 0) as tasks_total,
                    COALESCE(task_counts.completed_tasks, 0) as tasks_completed
                FROM projects p
                LEFT JOIN (
                    SELECT 
                        project_id,
                        COUNT(*) as total_tasks,
                        COUNT(CASE WHEN stage = 'COMPLETED' THEN 1 END) as completed_tasks
                    FROM tasks
                    GROUP BY project_id
                ) task_counts ON p.id = task_counts.project_id
                WHERE p.id = ?
            """),
            parameterSetter = { statement, projectId ->
                statement.setInt(1, projectId)
            },
            errorMapper = { GetProjectByIdFailures.DatabaseError },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    val location = if (resultSet.getObject("location_x") != null) {
                        MinecraftLocation(
                            x = resultSet.getInt("location_x"),
                            y = resultSet.getInt("location_y"),
                            z = resultSet.getInt("location_z"),
                            dimension = Dimension.valueOf(resultSet.getString("location_dimension"))
                        )
                    } else null

                    val tasksTotal = resultSet.getInt("tasks_total")
                    val tasksCompleted = resultSet.getInt("tasks_completed")
                    val stageProgress = if (tasksTotal > 0) {
                        tasksCompleted.toDouble() / tasksTotal.toDouble()
                    } else {
                        0.0
                    }

                    Project(
                        id = resultSet.getInt("id"),
                        worldId = resultSet.getInt("world_id"),
                        name = resultSet.getString("name"),
                        description = resultSet.getString("description"),
                        type = ProjectType.valueOf(resultSet.getString("type")),
                        stage = ProjectStage.valueOf(resultSet.getString("stage")),
                        stageProgress = stageProgress,
                        location = location,
                        tasksTotal = tasksTotal,
                        tasksCompleted = tasksCompleted,
                        createdAt = resultSet.getTimestamp("created_at").toInstant().atZone(java.time.ZoneOffset.UTC),
                        updatedAt = resultSet.getTimestamp("updated_at").toInstant().atZone(java.time.ZoneOffset.UTC)
                    )
                } else {
                    null
                }
            }
        )

        return when (val projectResult = projectStep.process(input)) {
            is Result.Success -> {
                val project = projectResult.getOrNull()
                if (project != null) {
                    Result.success(project)
                } else {
                    Result.failure(GetProjectByIdFailures.ProjectNotFound)
                }
            }
            is Result.Failure -> projectResult
        }
    }
}
