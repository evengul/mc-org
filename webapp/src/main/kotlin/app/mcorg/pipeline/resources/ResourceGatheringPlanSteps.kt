package app.mcorg.pipeline.resources

import app.mcorg.domain.model.resources.ProductionPath
import app.mcorg.domain.model.resources.ResourceGatheringPlan
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

val UpsertPathStep = DatabaseSteps.update<Pair<Int, String>>(
    sql = SafeSQL.insert(
        """
        INSERT INTO resource_gathering_plan (resource_gathering_id, selected_path, confirmed, created_at, updated_at)
        VALUES (?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (resource_gathering_id)
        DO UPDATE SET selected_path = EXCLUDED.selected_path, confirmed = FALSE, updated_at = CURRENT_TIMESTAMP
        RETURNING id
    """.trimIndent()
    ),
    parameterSetter = { ps, (resourceGatheringId, encodedPath) ->
        ps.setInt(1, resourceGatheringId)
        ps.setString(2, encodedPath)
    }
)

val LoadSavedPathStep = DatabaseSteps.query<Int, Result<AppFailure, ResourceGatheringPlan>>(
    sql = SafeSQL.select(
        """
        SELECT id, resource_gathering_id, selected_path, confirmed
        FROM resource_gathering_plan
        WHERE resource_gathering_id = ?
    """.trimIndent()
    ),
    parameterSetter = { ps, resourceGatheringId ->
        ps.setInt(1, resourceGatheringId)
    },
    resultMapper = { rs ->
        if (rs.next()) {
            val encodedPath = rs.getString("selected_path")
            val path = ProductionPath.decode(encodedPath)
            if (path != null) {
                Result.success(ResourceGatheringPlan(
                    id = rs.getInt("id"),
                    resourceGatheringId = rs.getInt("resource_gathering_id"),
                    selectedPath = path,
                    confirmed = rs.getBoolean("confirmed")
                ))
            } else Result.failure(AppFailure.DatabaseError.NotFound)
        } else Result.failure(AppFailure.DatabaseError.NotFound)
    }
)

val ConfirmPathStep = DatabaseSteps.update<Int>(
    sql = SafeSQL.update(
        """
        UPDATE resource_gathering_plan
        SET confirmed = TRUE, updated_at = CURRENT_TIMESTAMP
        WHERE resource_gathering_id = ?
    """.trimIndent()
    ),
    parameterSetter = { ps, resourceGatheringId ->
        ps.setInt(1, resourceGatheringId)
    }
)
