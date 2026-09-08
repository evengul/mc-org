package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.Comment
import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.RatingSummary
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.test.fixtures.TestDataFactory
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertTrue

/**
 * MCO-472 — the rating distribution renders `progressBar()`. It used to assert that the page
 * also linked `components/progress.css`, because the bar rendered as "5 ★ ⟨nothing⟩ 40%" when
 * it did not; since MCO-514 every sheet is in the one bundle and only the markup is left to check.
 */
class IdeaPageProgressStylesTest {

    private val user = TestDataFactory.createTestTokenProfile()

    private fun idea() = Idea(
        id = 1,
        name = "Iron Farm",
        description = "A tidy little iron farm",
        category = IdeaCategory.FARM,
        author = Author.SingleAuthor("Even"),
        subAuthors = emptyList(),
        labels = emptyList(),
        favouritesCount = 0,
        rating = RatingSummary(average = 4.0, total = 2),
        difficulty = IdeaDifficulty.MID_GAME,
        worksInVersionRange = MinecraftVersionRange.Unbounded,
        testData = emptyList(),
        categoryData = emptyMap(),
        createdBy = 1,
        createdAt = ZonedDateTime.now(),
    )

    private fun render(comments: List<Comment>) = ideaPage(
        user = user,
        idea = idea(),
        comments = comments,
    )

    private fun comment(id: Int, rating: Int) = Comment.RatingComment(
        id = id,
        ideaId = 1,
        commenterId = id,
        commenterName = "Rater $id",
        createdAt = ZonedDateTime.now(),
        likes = 0,
        rating = rating,
    )

    @Test
    fun `renders the rating bars as progress components`() {
        val html = render(listOf(comment(1, 5), comment(2, 3)))

        assertTrue(html.contains("idea-ratings__bar-row"), "rating rows should render")
        assertTrue(html.contains("class=\"progress\""), "each rating row should carry a bar")
    }
}
