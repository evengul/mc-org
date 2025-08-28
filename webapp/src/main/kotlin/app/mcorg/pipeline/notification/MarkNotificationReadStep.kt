package app.mcorg.pipeline.notification

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.model.notification.Notification
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.NotificationFailures
import java.sql.ResultSet

object MarkNotificationReadStep : Step<MarkNotificationInput, NotificationFailures, Notification> {
    override suspend fun process(input: MarkNotificationInput): Result<NotificationFailures, Notification> {
        return DatabaseSteps.update<MarkNotificationInput, NotificationFailures>(
            sql = SafeSQL.update("""
                UPDATE notifications 
                SET read_at = CURRENT_TIMESTAMP 
                WHERE id = ? AND user_id = ? AND read_at IS NULL
            """.trimIndent()),
            parameterSetter = { statement, markInput ->
                statement.setInt(1, markInput.notificationId)
                statement.setInt(2, markInput.userId)
            },
            errorMapper = { NotificationFailures.DatabaseError }
        ).process(input).flatMap { updateCount ->
            if (updateCount == 0) {
                Result.failure(NotificationFailures.NotificationNotFound)
            } else {
                // Fetch the updated notification
                GetNotificationByIdStep.process(input.notificationId to input.userId)
            }
        }
    }
}

object GetNotificationByIdStep : Step<Pair<Int, Int>, NotificationFailures, Notification> {
    override suspend fun process(input: Pair<Int, Int>): Result<NotificationFailures, Notification> {
        return DatabaseSteps.query<Pair<Int, Int>, NotificationFailures, Notification>(
            sql = SafeSQL.select("""
                SELECT 
                    id,
                    user_id,
                    title,
                    description,
                    type,
                    sent_at,
                    read_at,
                    link
                FROM notifications 
                WHERE id = ? AND user_id = ?
            """.trimIndent()),
            parameterSetter = { statement, idPair ->
                statement.setInt(1, idPair.first)
                statement.setInt(2, idPair.second)
            },
            errorMapper = { NotificationFailures.DatabaseError },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    resultSet.toNotification()
                } else {
                    throw IllegalStateException("Notification should exist at this point")
                }
            }
        ).process(input)
    }
}

// Extension function to convert ResultSet to Notification (shared utility)
private fun ResultSet.toNotification(): Notification {
    return Notification(
        id = getInt("id"),
        userId = getInt("user_id"),
        title = getString("title"),
        description = getString("description"),
        type = getString("type"),
        sentAt = getTimestamp("sent_at").toInstant().atZone(java.time.ZoneOffset.UTC),
        readAt = getTimestamp("read_at")?.toInstant()?.atZone(java.time.ZoneOffset.UTC),
        link = getString("link")
    )
}
