package app.mcorg.pipeline.project.commonsteps

import app.mcorg.domain.model.project.ProjectListItem
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class GetProjectListStep(val worldId: Int) : Step<Unit, AppFailure.DatabaseError, List<ProjectListItem>> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<ProjectListItem>> {
        return DatabaseSteps.query<Unit, List<ProjectListItem>>(
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
                WHERE p.world_id = ?
                GROUP BY p.id
                ORDER BY
                  CASE p.stage
                    WHEN 'RESOURCE_GATHERING' THEN 1
                    WHEN 'BUILDING'           THEN 2
                    WHEN 'TESTING'            THEN 3
                    WHEN 'IDEA'               THEN 4
                    WHEN 'DESIGN'             THEN 5
                    WHEN 'PLANNING'           THEN 6
                    WHEN 'COMPLETED'          THEN 7
                  END,
                  p.name ASC
            """.trimIndent()),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
            },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
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
                        )
                    }
                }
            }
        ).process(input)
    }
}
