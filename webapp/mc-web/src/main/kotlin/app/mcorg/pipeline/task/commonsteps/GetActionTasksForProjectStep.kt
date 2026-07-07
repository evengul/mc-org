package app.mcorg.pipeline.task.commonsteps

import app.mcorg.domain.model.task.ActionTask
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.extractors.toActionTask

/** All action tasks for a project, ordered by id. */
object GetActionTasksForProjectStep : Step<Int, AppFailure.DatabaseError, List<ActionTask>> by
    DatabaseSteps.query<Int, List<ActionTask>>(
        sql = SafeSQL.select(
            """
            SELECT t.id, t.project_id, t.name, t.completed
            FROM action_task t
            WHERE t.project_id = ?
            ORDER BY t.id
            """.trimIndent()
        ),
        parameterSetter = { statement, projectId -> statement.setInt(1, projectId) },
        resultMapper = { rs ->
            buildList {
                while (rs.next()) add(rs.toActionTask())
            }
        },
    )
