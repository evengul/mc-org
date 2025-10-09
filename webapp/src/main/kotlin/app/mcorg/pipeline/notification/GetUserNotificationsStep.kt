package app.mcorg.pipeline.notification

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.model.notification.Notification
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.NotificationFailures
import java.sql.ResultSet

object GetUserNotificationsStep : Step<GetNotificationsInput, NotificationFailures, List<Notification>> {
    override suspend fun process(input: GetNotificationsInput): Result<NotificationFailures, List<Notification>> {
        return DatabaseSteps.query<GetNotificationsInput, NotificationFailures, List<Notification>>(
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
                WHERE user_id = ?
                ${if (input.unreadOnly) "AND read_at IS NULL" else ""}
                ORDER BY 
                    CASE WHEN read_at IS NULL THEN 0 ELSE 1 END,
                    sent_at DESC
                LIMIT ? OFFSET ?
            """.trimIndent()),
            parameterSetter = { statement, notificationInput ->
                statement.setInt(1, notificationInput.userId)
                statement.setInt(2, notificationInput.limit)
                statement.setInt(3, notificationInput.offset)
            },
            errorMapper = { NotificationFailures.DatabaseError },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.toNotification())
                    }
                }
            }
        ).process(input)
    }
}

// Extension function to convert ResultSet to Notification
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
