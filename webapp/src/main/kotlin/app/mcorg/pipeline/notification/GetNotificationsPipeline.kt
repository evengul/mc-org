package app.mcorg.pipeline.notification

import app.mcorg.domain.model.notification.Notification
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.notification.extractors.toNotifications
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.notification.notificationsPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*

suspend fun ApplicationCall.handleGetNotifications() {
    val user = getUser()
    val unreadOnly = parameters["unread"]?.toBoolean() ?: false
    val limit = parameters["limit"]?.toIntOrNull() ?: 20
    val offset = parameters["offset"]?.toIntOrNull() ?: 0

    val input = GetNotificationsInput(
        userId = user.id,
        limit = limit,
        offset = offset,
        unreadOnly = unreadOnly
    )

    executePipeline(
        onSuccess = { notifications ->
            val unreadCount = notifications.count { it.readAt == null }
            respondHtml(notificationsPage(user, notifications, unreadOnly, unreadCount))
        }
    ) {
        value(input)
            .step(GetUserNotificationsStep)
    }
}

private data class GetNotificationsInput(
    val userId: Int,
    val limit: Int = 20,
    val offset: Int = 0,
    val unreadOnly: Boolean = false
)

private object GetUserNotificationsStep : Step<GetNotificationsInput, AppFailure.DatabaseError, List<Notification>> {
    override suspend fun process(input: GetNotificationsInput): Result<AppFailure.DatabaseError, List<Notification>> {
        return DatabaseSteps.query<GetNotificationsInput, List<Notification>>(
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
            resultMapper = { it.toNotifications() }
        ).process(input)
    }
}