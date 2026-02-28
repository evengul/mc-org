package app.mcorg.domain.model.idea

import java.time.ZonedDateTime

sealed class Comment(
    val id: Int,
    val ideaId: Int,
    val commenterId: Int,
    val commenterName: String,
    val createdAt: ZonedDateTime,
    val likes: Int,
    val youLiked: Boolean = false
) {
    class TextComment(
        id: Int,
        ideaId: Int,
        commenterId: Int,
        commenterName: String,
        createdAt: ZonedDateTime,
        likes: Int,
        val content: String,
        youLiked: Boolean = false
    ) : Comment(
        id = id,
        ideaId = ideaId,
        commenterId = commenterId,
        commenterName = commenterName,
        createdAt = createdAt,
        likes = likes,
        youLiked = youLiked
    )

    class RatingComment(
        id: Int,
        ideaId: Int,
        commenterId: Int,
        commenterName: String,
        createdAt: ZonedDateTime,
        likes: Int,
        youLiked: Boolean = false,
        val rating: Int,
    ) : Comment(
        id = id,
        ideaId = ideaId,
        commenterId = commenterId,
        commenterName = commenterName,
        createdAt = createdAt,
        likes = likes,
        youLiked = youLiked
    )

    class RatedTextComment(
        id: Int,
        ideaId: Int,
        commenterId: Int,
        commenterName: String,
        createdAt: ZonedDateTime,
        likes: Int,
        val content: String,
        val rating: Int,
        youLiked: Boolean = false
    ) : Comment(
        id = id,
        ideaId = ideaId,
        commenterId = commenterId,
        commenterName = commenterName,
        createdAt = createdAt,
        likes = likes,
        youLiked = youLiked
    )
}
