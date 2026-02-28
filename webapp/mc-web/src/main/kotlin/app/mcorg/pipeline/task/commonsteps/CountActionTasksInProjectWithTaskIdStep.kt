package app.mcorg.pipeline.task.commonsteps

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

val CountActionTasksInProjectWithTaskIdStep = DatabaseSteps.query<Int, Int>(
    sql = SafeSQL.select("""
                SELECT COUNT(id) FROM action_task WHERE project_id = (
                    SELECT project_id FROM action_task WHERE id = ?
                )
            """.trimIndent()),
    parameterSetter = { statement, taskId -> statement.setInt(1, taskId) },
    resultMapper = { rs ->
        if (rs.next()) {
            rs.getInt(1)
        } else {
            0
        }
    }
)

