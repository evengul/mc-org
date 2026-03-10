package app.mcorg.pipeline.project.commonsteps

import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class SetViewPreferenceInput(val userId: Int, val projectId: Int, val preference: String)

object SetViewPreferenceStep : Step<SetViewPreferenceInput, AppFailure, Unit> {
    private val validPreferences = setOf("plan", "execute")

    override suspend fun process(input: SetViewPreferenceInput): Result<AppFailure, Unit> {
        if (input.preference !in validPreferences) {
            return Result.failure(
                AppFailure.ValidationError(
                    listOf(app.mcorg.pipeline.failure.ValidationFailure.InvalidValue("preference", listOf("plan", "execute")))
                )
            )
        }

        val step = DatabaseSteps.update<SetViewPreferenceInput>(
            sql = SafeSQL.insert("""
                INSERT INTO user_project_view_preference (user_id, project_id, view_preference, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (user_id, project_id)
                DO UPDATE SET view_preference = EXCLUDED.view_preference, updated_at = now()
            """.trimIndent()),
            parameterSetter = { stmt, inp ->
                stmt.setInt(1, inp.userId)
                stmt.setInt(2, inp.projectId)
                stmt.setString(3, inp.preference)
            }
        )

        return when (val result = step.process(input)) {
            is Result.Success -> Result.success(Unit)
            is Result.Failure -> result
        }
    }
}
