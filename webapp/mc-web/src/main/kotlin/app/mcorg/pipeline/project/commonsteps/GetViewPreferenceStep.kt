package app.mcorg.pipeline.project.commonsteps

import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class GetViewPreferenceInput(val userId: Int, val projectId: Int)

object GetViewPreferenceStep : Step<GetViewPreferenceInput, AppFailure.DatabaseError, String> {
    override suspend fun process(input: GetViewPreferenceInput): Result<AppFailure.DatabaseError, String> {
        val step = DatabaseSteps.query<GetViewPreferenceInput, String?>(
            sql = SafeSQL.select("SELECT view_preference FROM user_project_view_preference WHERE user_id = ? AND project_id = ?"),
            parameterSetter = { stmt, inp ->
                stmt.setInt(1, inp.userId)
                stmt.setInt(2, inp.projectId)
            },
            resultMapper = { rs ->
                if (rs.next()) rs.getString("view_preference") else null
            }
        )

        return when (val result = step.process(input)) {
            is Result.Success -> Result.success(result.value ?: "execute")
            is Result.Failure -> result
        }
    }
}
