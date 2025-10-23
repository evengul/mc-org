package app.mcorg.pipeline.admin

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure

data object CountManagedWorldsStep : Step<String, DatabaseFailure, Int> {
    override suspend fun process(input: String): Result<DatabaseFailure, Int> {
        return DatabaseSteps.query<String, DatabaseFailure, Int>(
            SafeSQL.select("""
                SELECT COUNT(*) as total_worlds FROM world WHERE ? = '' OR LOWER(name) LIKE LOWER(CONCAT('%', ?, '%'))
            """.trimIndent()),
            parameterSetter = { ps, query ->
                ps.setString(1, query)
                ps.setString(2, query)
            },
            errorMapper = { it },
            resultMapper = { rs ->
                if (rs.next()) {
                    rs.getInt("total_worlds")
                } else {
                    0
                }
            }
        ).process(input)
    }
}