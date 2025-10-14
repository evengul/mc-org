package app.mcorg.domain.model.idea

import java.time.ZonedDateTime

data class Comment(
    val id: Int,
    val ideaId: Int,
    val commenterId: Int,
    val commenterName: String,
    val createdAt: ZonedDateTime,
    val likes: Int,
    val content: String
)
