package app.mcorg.pipeline.admin.commonsteps

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

val CountManagedUsersStep = DatabaseSteps.query<String, Int>(
    SafeSQL.select("""
                SELECT COUNT(*) as total_users FROM minecraft_profiles WHERE ? = '' OR LOWER(username) LIKE LOWER(CONCAT('%', ?, '%'))
            """.trimIndent()),
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
)