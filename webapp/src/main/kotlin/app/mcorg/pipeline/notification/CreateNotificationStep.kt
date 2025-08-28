package app.mcorg.pipeline.notification

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.model.notification.Notification
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.NotificationFailures

object CreateNotificationStep : Step<CreateNotificationInput, NotificationFailures, Notification> {
    override suspend fun process(input: CreateNotificationInput): Result<NotificationFailures, Notification> {
        return DatabaseSteps.update<CreateNotificationInput, NotificationFailures>(
            sql = SafeSQL.insert("""
                INSERT INTO notifications (user_id, title, description, type, link, sent_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING id
            """.trimIndent()),
            parameterSetter = { statement, notificationInput ->
                statement.setInt(1, notificationInput.userId)
                statement.setString(2, notificationInput.title)
                statement.setString(3, notificationInput.description)
                statement.setString(4, notificationInput.type)
                statement.setString(5, notificationInput.link)
            },
            errorMapper = { NotificationFailures.DatabaseError }
        ).process(input).flatMap { notificationId ->
            // Fetch the created notification
            GetNotificationByIdStep.process(notificationId to input.userId)
        }
    }
}

// Notification type constants for system use
object NotificationTypes {
    const val INVITATION_RECEIVED = "invitation_received"
    const val INVITATION_ACCEPTED = "invitation_accepted"
    const val PROJECT_ASSIGNED = "project_assigned"
    const val TASK_COMPLETED = "task_completed"
    const val WORLD_ROLE_CHANGED = "world_role_changed"
    const val PROJECT_STAGE_CHANGED = "project_stage_changed"
}
