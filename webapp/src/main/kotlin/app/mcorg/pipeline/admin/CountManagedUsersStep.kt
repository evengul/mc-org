package app.mcorg.pipeline.admin

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure

object CountManagedUsersStep : Step<String, DatabaseFailure, Int> {
    override suspend fun process(input: String): Result<DatabaseFailure, Int> {
        return DatabaseSteps.query<String, DatabaseFailure, Int>(
            SafeSQL.select("""
                SELECT COUNT(*) as total_users FROM minecraft_profiles WHERE ? = '' OR LOWER(username) LIKE LOWER(CONCAT('%', ?, '%'))
            """.trimIndent()),
            errorMapper = { it },
            parameterSetter = { ps, query ->
                ps.setString(1, query)
                ps.setString(2, query)
            },
            resultMapper = { rs ->
                if (rs.next()) {
                    rs.getInt("total_users")
                } else {
                    0
                }
            }
        ).process(input)
    }
}