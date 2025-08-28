package app.mcorg.pipeline.notification

import app.mcorg.domain.model.notification.Notification

// Input data classes for notification operations
data class GetNotificationsInput(
    val userId: Int,
    val limit: Int = 20,
    val offset: Int = 0,
    val unreadOnly: Boolean = false
)

data class MarkNotificationInput(
    val userId: Int,
    val notificationId: Int
)

data class CreateNotificationInput(
    val userId: Int,
    val title: String,
    val description: String,
    val type: String,
    val link: String? = null
)

data class BulkMarkResult(
    val updatedCount: Int,
    val notifications: List<Notification>
)
