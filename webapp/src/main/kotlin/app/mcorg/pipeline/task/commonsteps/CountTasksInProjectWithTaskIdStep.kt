package app.mcorg.pipeline.task.commonsteps

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

val CountTasksInProjectWithTaskIdStep = DatabaseSteps.query<Int, Int>(
    sql = SafeSQL.select("""
                SELECT COUNT(id) FROM tasks WHERE project_id = (
                    SELECT project_id FROM tasks WHERE id = ?
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