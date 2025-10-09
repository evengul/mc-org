package app.mcorg.pipeline.notification

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.NotificationFailures

object BulkMarkNotificationsReadStep : Step<Int, NotificationFailures, BulkMarkResult> {
    override suspend fun process(input: Int): Result<NotificationFailures, BulkMarkResult> {
        return DatabaseSteps.update<Int, NotificationFailures>(
            sql = SafeSQL.update("""
                UPDATE notifications 
                SET read_at = CURRENT_TIMESTAMP 
                WHERE user_id = ? AND read_at IS NULL
            """.trimIndent()),
            parameterSetter = { statement, userId ->
                statement.setInt(1, userId)
            },
            errorMapper = { NotificationFailures.DatabaseError }
        ).process(input).flatMap { updateCount ->
            // Get updated notifications list
            GetUserNotificationsStep.process(
                GetNotificationsInput(
                    userId = input,
                    limit = 50, // Get a reasonable number for display
                    offset = 0,
                    unreadOnly = false
                )
            ).map { notifications ->
                BulkMarkResult(
                    updatedCount = updateCount,
                    notifications = notifications
                )
            }
        }
    }
}
