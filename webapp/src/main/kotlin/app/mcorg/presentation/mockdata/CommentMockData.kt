package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.idea.Comment
import java.time.ZonedDateTime

object CommentMockData {
    val firstComment = Comment(
        id = 1,
        ideaId = IdeaMockData.sugarcaneFarm.id,
        commenterId = UserMockData.Alex.id,
        commenterName = UserMockData.Alex.name,
        createdAt = ZonedDateTime.now(),
        likes = 3,
        content = "This is a great idea! I've been looking for something like this."
    )

    val secondComment = Comment(
        id = 2,
        ideaId = IdeaMockData.sugarcaneFarm.id,
        commenterId = UserMockData.Steve.id,
        commenterName = UserMockData.Steve.name,
        createdAt = ZonedDateTime.now().minusDays(1),
        likes = 1,
        content = "I built something similar in my survival world. It works really well!"
    )

    val allComments = listOf(firstComment, secondComment)
}