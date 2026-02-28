package app.mcorg.pipeline.notification.commonsteps

import app.mcorg.domain.model.notification.Notification
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.notification.extractors.toNotification

val GetNotificationByIdStep = DatabaseSteps.query<Pair<Int, Int>, Notification>(
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
    resultMapper = { resultSet ->
        if (resultSet.next()) {
            resultSet.toNotification()
        } else {
            throw IllegalStateException("Notification should exist at this point")
        }
    }
)