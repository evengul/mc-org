package app.mcorg.domain.model.notification

import java.time.ZonedDateTime

data class Notification(
    val id: Int,
    val userId: Int,
    val title: String,
    val description: String,
    val type: String,
    val sentAt: ZonedDateTime,
    val readAt: ZonedDateTime? = null,
    val link: String? = null
)
