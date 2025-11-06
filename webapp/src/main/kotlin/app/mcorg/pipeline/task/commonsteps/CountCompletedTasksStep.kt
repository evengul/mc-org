package app.mcorg.pipeline.task.commonsteps

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

val CountCompletedTasksStep = DatabaseSteps.query<Int, Int>(
    sql = SafeSQL.select("""
                SELECT COUNT(id) FROM tasks WHERE project_id = (
                    SELECT project_id FROM tasks WHERE id = ?
                ) AND (
                    (requirement_type = 'ITEM' AND requirement_item_collected >= requirement_item_required_amount)
                    OR
                    (requirement_type = 'ACTION' AND requirement_action_completed = TRUE)
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