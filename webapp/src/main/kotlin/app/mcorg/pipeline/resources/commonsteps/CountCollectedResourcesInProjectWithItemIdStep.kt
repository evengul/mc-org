package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

val CountCollectedResourcesInProjectWithItemIdStep = DatabaseSteps.query<Int, Int>(
    sql = SafeSQL.select("""
                SELECT SUM(collected) FROM resource_gathering WHERE project_id = (
                    SELECT project_id FROM resource_gathering WHERE id = ?
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