package app.mcorg.domain.model.idea

import java.time.ZonedDateTime

data class Rating(
    val id: Int,
    val ideaId: Int,
    val raterId: Int,
    val raterName: String,
    val score: Double,
    val content: String?,
    val createdAt: ZonedDateTime
)
