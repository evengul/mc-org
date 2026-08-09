package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.RatingSummary
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers MCO-309: the detail page renders `categoryData` against the category schema.
 * The interesting behaviour is what gets *skipped* — unknown keys and empty values —
 * and the map-valued fields (`specs`, `productionRate`, `size`) that carry the detail.
 */
class IdeaDetailFieldsTest {

    private fun render(block: FlowContent.() -> Unit): String = createHTML().div { block() }

    private fun idea(
        category: IdeaCategory = IdeaCategory.FARM,
        categoryData: Map<String, CategoryValue> = emptyMap(),
    ) = Idea(
        id = 1,
        name = "Iron Farm",
        description = "A tidy little iron farm",
        category = category,
        author = Author.SingleAuthor("Even"),
        subAuthors = emptyList(),
        labels = emptyList(),
        favouritesCount = 0,
        rating = RatingSummary(average = 0.0, total = 0),
        difficulty = IdeaDifficulty.MID_GAME,
        worksInVersionRange = MinecraftVersionRange.Unbounded,
        testData = emptyList(),
        categoryData = categoryData,
        createdBy = 1,
        createdAt = ZonedDateTime.now(),
    )

    @Test
    fun `renders nothing when there is no category data`() {
        val html = render { ideaDetailFields(idea()) }

        assertFalse(html.contains("Design details"))
        assertFalse(html.contains("idea-detail__fields"))
    }

    @Test
    fun `renders the free-form specs block as label-value pairs`() {
        val html = render {
            ideaDetailFields(
                idea(
                    categoryData = mapOf(
                        "specs" to CategoryValue.MapValue(
                            mapOf(
                                "TNT per piston" to CategoryValue.TextValue("10"),
                                "Remaining fuse" to CategoryValue.TextValue("21gt"),
                            )
                        )
                    )
                )
            )
        }

        assertTrue(html.contains("Design details"))
        assertTrue(html.contains("Specs"))
        assertTrue(html.contains("TNT per piston"))
        assertTrue(html.contains("10"))
        assertTrue(html.contains("Remaining fuse"))
        assertTrue(html.contains("21gt"))
    }

    @Test
    fun `free-form spec keys are shown exactly as the submitter typed them`() {
        val html = render {
            ideaDetailFields(
                idea(
                    categoryData = mapOf(
                        "specs" to CategoryValue.MapValue(
                            mapOf("gt per cycle" to CategoryValue.TextValue("8"))
                        )
                    )
                )
            )
        }

        assertTrue(html.contains("gt per cycle"))
    }

    @Test
    fun `production rate item ids are tidied and carry their unit`() {
        val html = render {
            ideaDetailFields(
                idea(
                    categoryData = mapOf(
                        "productionRate" to CategoryValue.MapValue(
                            mapOf("minecraft:iron_ingot" to CategoryValue.IntValue(1200))
                        )
                    )
                )
            )
        }

        assertTrue(html.contains("Iron Ingot"))
        assertFalse(html.contains("minecraft:iron_ingot"))
        assertTrue(html.contains("1200 items/hour"))
    }

    @Test
    fun `a complete size renders as dimensions`() {
        val html = render {
            ideaDetailFields(
                idea(
                    categoryData = mapOf(
                        "size" to CategoryValue.MapValue(
                            mapOf(
                                "x" to CategoryValue.IntValue(12),
                                "y" to CategoryValue.IntValue(4),
                                "z" to CategoryValue.IntValue(9),
                            )
                        )
                    )
                )
            )
        }

        assertTrue(html.contains("12 × 4 × 9"))
    }

    @Test
    fun `a partial size falls back to labelled pairs`() {
        val html = render {
            ideaDetailFields(
                idea(categoryData = mapOf("size" to CategoryValue.MapValue(mapOf("x" to CategoryValue.IntValue(12)))))
            )
        }

        assertFalse(html.contains("12 ×"))
        assertTrue(html.contains("X Dimension"))
        assertTrue(html.contains("12"))
    }

    @Test
    fun `reference urls become links and non-urls stay text`() {
        val html = render {
            ideaDetailFields(
                idea(
                    categoryData = mapOf(
                        "references" to CategoryValue.MultiSelectValue(
                            setOf("https://youtu.be/abc", "asked Steve in chat")
                        )
                    )
                )
            )
        }

        assertTrue(html.contains("""<a href="https://youtu.be/abc""""))
        assertTrue(html.contains("asked Steve in chat"))
        assertFalse(html.contains("""<a href="asked Steve"""))
    }

    @Test
    fun `booleans render as yes and no`() {
        val html = render {
            ideaDetailFields(
                idea(
                    categoryData = mapOf(
                        "afkable" to CategoryValue.BooleanValue(true),
                        "tileable" to CategoryValue.BooleanValue(false),
                    )
                )
            )
        }

        assertTrue(html.contains("AFK-able"))
        assertTrue(html.contains("Yes"))
        assertTrue(html.contains("Tileable"))
        assertTrue(html.contains("No"))
    }

    @Test
    fun `keys the schema no longer defines are skipped`() {
        val html = render {
            ideaDetailFields(
                idea(
                    categoryData = mapOf(
                        // A pre-MCO-204 field, left behind in an older row's JSONB.
                        "witherSkeletonSkullsPerHour" to CategoryValue.IntValue(3),
                        "afkable" to CategoryValue.BooleanValue(true),
                    )
                )
            )
        }

        assertFalse(html.contains("witherSkeletonSkullsPerHour"))
        assertTrue(html.contains("AFK-able"))
    }

    @Test
    fun `empty values do not earn a row`() {
        val html = render {
            ideaDetailFields(
                idea(
                    categoryData = mapOf(
                        "specs" to CategoryValue.MapValue(emptyMap()),
                        "references" to CategoryValue.MultiSelectValue(emptySet()),
                        "storageType" to CategoryValue.TextValue("  "),
                    )
                )
            )
        }

        assertFalse(html.contains("Design details"))
    }

    @Test
    fun `fields follow schema order rather than map order`() {
        val html = render {
            ideaDetailFields(
                idea(
                    categoryData = mapOf(
                        "afkable" to CategoryValue.BooleanValue(true),
                        "size" to CategoryValue.MapValue(
                            mapOf(
                                "x" to CategoryValue.IntValue(1),
                                "y" to CategoryValue.IntValue(2),
                                "z" to CategoryValue.IntValue(3),
                            )
                        ),
                    )
                )
            )
        }

        // FARM declares size before afkable.
        assertTrue(html.indexOf("Size") < html.indexOf("AFK-able"))
    }
}
