package app.mcorg.domain.model.idea

import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import java.time.ZonedDateTime

data class Idea(
    val id: Int,
    val name: String,
    val description: String,
    val category: IdeaCategory,
    val creatorId: Int,
    val creatorName: String,
    val createdAt: ZonedDateTime,
    val labels: List<String>,
    val favouritesCount: Int,
    val rating: RatingSummary,
    val difficulty: IdeaDifficulty,
    val worksInVersionRange: MinecraftVersionRange
)
