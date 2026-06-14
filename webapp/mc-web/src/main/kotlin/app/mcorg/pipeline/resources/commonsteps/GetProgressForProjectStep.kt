package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

/**
 * Loads all collected-progress rows for a project as a map of itemId -> collected count.
 * Items with no progress row are absent from the map (callers should COALESCE to 0).
 */
val GetProgressForProjectStep = DatabaseSteps.query<Int, Map<String, Int>>(
    sql = SafeSQL.select(
        "SELECT item_id, collected FROM resource_gathering_progress WHERE project_id = ?"
    ),
    parameterSetter = { stmt, projectId -> stmt.setInt(1, projectId) },
    resultMapper = { rs ->
        buildMap {
            while (rs.next()) {
                put(rs.getString("item_id"), rs.getInt("collected"))
            }
        }
    }
)
