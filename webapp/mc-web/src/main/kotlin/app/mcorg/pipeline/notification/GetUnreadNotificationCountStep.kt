package app.mcorg.pipeline.notification

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

suspend fun getUnreadNotificationsOrZero(userId: Int): Int {
    // Check cache first
    CacheManager.unreadNotificationCount.getIfPresent(userId)?.let { return it }

    // Cache miss - query DB
    val count = GetUnreadNotificationCountStep.process(userId)
        .getOrNull() ?: 0

    CacheManager.unreadNotificationCount.put(userId, count)
    return count
}

private val GetUnreadNotificationCountStep = DatabaseSteps.query<Int, Int>(
    sql = SafeSQL.select("""
                SELECT COUNT(*)
                FROM notifications
                WHERE user_id = ? AND read_at IS NULL
            """.trimIndent()),
    parameterSetter = { statement, userId ->
        statement.setInt(1, userId)
    },
    resultMapper = { resultSet ->
        if (resultSet.next()) {
            resultSet.getInt(1)
        } else {
            0
        }
    }
)
