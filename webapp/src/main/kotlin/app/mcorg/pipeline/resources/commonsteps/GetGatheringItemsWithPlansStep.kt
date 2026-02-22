package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

/**
 * Returns the set of resource_gathering IDs (for a given project) that already have a saved plan.
 */
val GetGatheringItemsWithPlansStep = DatabaseSteps.query<Int, Set<Int>>(
    sql = SafeSQL.select(
        """
        SELECT rg.id
        FROM resource_gathering rg
        INNER JOIN resource_gathering_plan p ON p.resource_gathering_id = rg.id
        WHERE rg.project_id = ?
        """.trimIndent()
    ),
    parameterSetter = { ps, projectId -> ps.setInt(1, projectId) },
    resultMapper = { rs ->
        val ids = mutableSetOf<Int>()
        while (rs.next()) ids.add(rs.getInt("id"))
        ids
    }
)
