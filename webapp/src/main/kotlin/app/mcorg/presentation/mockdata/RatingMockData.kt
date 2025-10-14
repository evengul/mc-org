package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.idea.Rating
import java.time.ZonedDateTime

object RatingMockData {
    val exceptionalRating = Rating(
        id = 1,
        ideaId = IdeaMockData.sugarcaneFarm.id,
        raterId = UserMockData.Alex.id,
        raterName = UserMockData.Alex.name,
        score = 5.0,
        content = "This worked perfectly in my survival world! Highly recommended.",
        createdAt = ZonedDateTime.now()
    )

    val goodRating = Rating(
        id = 2,
        ideaId = IdeaMockData.sugarcaneFarm.id,
        raterId = UserMockData.Steve.id,
        raterName = UserMockData.Steve.name,
        score = 4.0,
        content = "Great design, but I had to modify it slightly for my needs.",
        createdAt = ZonedDateTime.now().minusDays(1)
    )

    val mediumRating = Rating(
        id = 3,
        ideaId = IdeaMockData.villagerTradingHall.id,
        raterId = UserMockData.Creeper.id,
        raterName = UserMockData.Creeper.name,
        score = 3.0,
        content = "Decent idea but required more resources than expected.",
        createdAt = ZonedDateTime.now().minusDays(2)
    )

    val allRatings = listOf(goodRating, goodRating, mediumRating)
}