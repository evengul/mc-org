package app.mcorg.domain.model.idea

import java.time.ZonedDateTime

data class IdeaDraft(
    val id: Int,
    val userId: Int,
    val data: String,
    val currentStage: String,
    val sourceIdeaId: Int?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)
