package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure

object CountProjectTasksStep : Step<Int, DatabaseFailure, Int> {
    override suspend fun process(input: Int): Result<DatabaseFailure, Int> {
        return DatabaseSteps.query<Int, DatabaseFailure, Int>(
            sql = SafeSQL.select("""SELECT COUNT(id) FROM tasks WHERE project_id = ?"""),
            parameterSetter = { statement, projectId ->
                statement.setInt(1, projectId)
            },
            errorMapper = { it },
            resultMapper = { rs ->
                if (rs.next()) {
                    rs.getInt(1)
                } else {
                    0
                }
            }
        ).process(input)
    }
}