package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

val CountCollectedResourcesInProjectWithItemIdStep = DatabaseSteps.query<Int, Int>(
    sql = SafeSQL.select(
        """
        SELECT COALESCE(SUM(rgp.collected), 0)
        FROM resource_gathering_progress rgp
        WHERE rgp.project_id = (
            SELECT project_id FROM resource_gathering WHERE id = ?
        )
        """.trimIndent()
    ),
    parameterSetter = { statement, taskId -> statement.setInt(1, taskId) },
    resultMapper = { rs ->
        if (rs.next()) rs.getInt(1) else 0
    }
)
