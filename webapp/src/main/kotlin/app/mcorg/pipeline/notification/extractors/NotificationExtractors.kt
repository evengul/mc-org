package app.mcorg.pipeline.notification.extractors

import app.mcorg.domain.model.notification.Notification
import java.sql.ResultSet

fun ResultSet.toNotifications() = buildList {
    while (next()) {
        add(toNotification())
    }
}

fun ResultSet.toNotification(): Notification {
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