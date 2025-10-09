package app.mcorg.pipeline.notification

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.NotificationFailures

object GetUnreadNotificationCountStep : Step<Int, NotificationFailures, Int> {
    override suspend fun process(input: Int): Result<NotificationFailures, Int> {
        return DatabaseSteps.query<Int, NotificationFailures, Int>(
            sql = SafeSQL.select("""
                SELECT COUNT(*) 
                FROM notifications 
                WHERE user_id = ? AND read_at IS NULL
            """.trimIndent()),
            parameterSetter = { statement, userId ->
                statement.setInt(1, userId)
            },
            errorMapper = { NotificationFailures.DatabaseError },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    resultSet.getInt(1)
                } else {
                    0
                }
            }
        ).process(input)
    }
}
