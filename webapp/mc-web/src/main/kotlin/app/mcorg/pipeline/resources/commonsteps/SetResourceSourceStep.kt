package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import java.sql.Types

sealed interface ResourceSourceAssignment {
    object Manual : ResourceSourceAssignment
    data class LinkedProject(val projectId: Int) : ResourceSourceAssignment
}

data class SetResourceSourceStep(val resourceGatheringId: Int) : Step<ResourceSourceAssignment, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: ResourceSourceAssignment): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<ResourceSourceAssignment>(
            sql = SafeSQL.update(
                """
                UPDATE resource_gathering
                SET source_type = ?, solved_by_project_id = ?, updated_at = NOW()
                WHERE id = ?
                """.trimIndent()
            ),
            parameterSetter = { stmt, assignment ->
                when (assignment) {
                    is ResourceSourceAssignment.Manual -> {
                        stmt.setString(1, "manual")
                        stmt.setNull(2, Types.INTEGER)
                    }
                    is ResourceSourceAssignment.LinkedProject -> {
                        stmt.setString(1, "project")
                        stmt.setInt(2, assignment.projectId)
                    }
                }
                stmt.setInt(3, resourceGatheringId)
            }
        ).process(input).map { }
    }
}
