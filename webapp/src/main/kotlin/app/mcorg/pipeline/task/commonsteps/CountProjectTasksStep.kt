package app.mcorg.pipeline.task.commonsteps

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

val CountProjectTasksStep = DatabaseSteps.query<Int, Int>(
    sql = SafeSQL.select("""SELECT COUNT(id) FROM tasks WHERE project_id = ?"""),
    parameterSetter = { statement, projectId ->
        statement.setInt(1, projectId)
    },
    resultMapper = { rs ->
        if (rs.next()) {
            rs.getInt(1)
        } else {
            0
        }
    }
)