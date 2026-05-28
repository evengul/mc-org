package app.mcorg.pipeline.project.commonsteps

import app.mcorg.domain.model.project.ProjectListItem
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

object GetProjectListItemStep : Step<Int, AppFailure.DatabaseError, ProjectListItem> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, ProjectListItem> {
        return DatabaseSteps.query<Int, ProjectListItem?>(
            sql = SafeSQL.select("""
                SELECT
                  p.id,
                  p.name,
                  p.stage,
                  COUNT(DISTINCT t.id) FILTER (WHERE t.completed = false) AS tasks_remaining,
                  COUNT(DISTINCT t.id)                                      AS tasks_total,
                  COALESCE(SUM(rg.required), 0)                            AS resources_required,
                  COALESCE(SUM(rg.collected), 0)                           AS resources_gathered,
                  (
                    SELECT t2.name FROM action_task t2
                    WHERE t2.project_id = p.id AND t2.completed = false
                    ORDER BY t2.id ASC
                    LIMIT 1
                  ) AS next_task_name
                FROM projects p
                LEFT JOIN action_task t  ON t.project_id  = p.id
                LEFT JOIN resource_gathering rg ON rg.project_id = p.id
                WHERE p.id = ?
                GROUP BY p.id
            """.trimIndent()),
            parameterSetter = { statement, projectId ->
                statement.setInt(1, projectId)
            },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    ProjectListItem(
                        id = resultSet.getInt("id"),
                        name = resultSet.getString("name"),
                        stage = ProjectStage.valueOf(resultSet.getString("stage")),
                        tasksTotal = resultSet.getInt("tasks_total"),
                        tasksDone = resultSet.getInt("tasks_total") - resultSet.getInt("tasks_remaining"),
                        resourcesRequired = resultSet.getInt("resources_required"),
                        resourcesGathered = resultSet.getInt("resources_gathered"),
                        nextTaskName = resultSet.getString("next_task_name")
                    )
                } else null
            }
        ).process(input).flatMap {
            if (it == null) {
                Result.failure(AppFailure.DatabaseError.NotFound)
            } else {
                Result.success(it)
            }
        }
    }
}
