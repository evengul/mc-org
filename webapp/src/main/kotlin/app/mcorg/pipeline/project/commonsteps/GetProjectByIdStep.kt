package app.mcorg.pipeline.project.commonsteps

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.project.extractors.toProject

object GetProjectByIdStep : Step<Int, AppFailure.DatabaseError, Project> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Project> {
        val projectStep = DatabaseSteps.query<Int, Project?>(
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
                    COALESCE(task_stats.tasks_total, 0) as tasks_total,
                    COALESCE(task_stats.tasks_completed, 0) as tasks_completed
                FROM projects p
                LEFT JOIN (
                    SELECT 
                        t.project_id,
                        COUNT(t.id) as tasks_total,
                        COUNT(ct.id) as tasks_completed
                    FROM tasks t
                    LEFT JOIN (
                        SELECT t2.id
                        FROM tasks t2
                        GROUP BY t2.id
                        HAVING COUNT(*) = SUM(
                            CASE
                                WHEN t2.requirement_type = 'ITEM' AND t2.requirement_item_collected >= t2.requirement_item_required_amount THEN 1
                                WHEN t2.requirement_type = 'ACTION' AND t2.requirement_action_completed = true THEN 1
                                ELSE 0
                            END
                        )
                    ) ct ON t.id = ct.id
                    GROUP BY t.project_id
                ) task_stats ON p.id = task_stats.project_id
                WHERE p.id = ?
            """.trimIndent()),
            parameterSetter = { statement, projectId ->
                statement.setInt(1, projectId)
            },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    resultSet.toProject()
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
                    Result.failure(AppFailure.DatabaseError.NotFound)
                }
            }
            is Result.Failure -> projectResult
        }
    }
}