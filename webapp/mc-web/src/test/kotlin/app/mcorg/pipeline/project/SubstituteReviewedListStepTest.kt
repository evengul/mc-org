package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * MCO-304 — reading the review form back, one family swap at a time.
 */
class SubstituteReviewedListStepTest {

    private val woods = listOf("oak", "spruce", "birch", "jungle", "acacia")

    private val catalog: List<Item> = buildList {
        woods.forEach { wood ->
            listOf("planks", "slab", "stairs").forEach { form ->
                add(Item("minecraft:${wood}_$form", "${wood.replaceFirstChar { it.uppercase() }} $form"))
            }
        }
        add(Item("minecraft:stone", "Stone"))
    }

    private val step = SubstituteReviewedListStep(catalog)

    private fun form(vararg pairs: Pair<String, String>) = Parameters.build {
        pairs.forEach { (key, value) -> append(key, value) }
    }

    private fun run(params: Parameters) = runBlocking { step.process(params) }

    private fun succeed(params: Parameters): ReviewedList {
        val result = run(params)
        assertIs<Result.Success<ReviewedList>>(result, "expected success, got $result")
        return result.value
    }

    @Test
    fun `every row in the family moves and everything else stays`() {
        val reviewed = succeed(
            form(
                "from" to "oak",
                "to[oak]" to "spruce",
                "row[minecraft:oak_planks]" to "64",
                "qty[minecraft:oak_planks]" to "64",
                "row[minecraft:stone]" to "12",
                "qty[minecraft:stone]" to "12",
            )
        )

        assertEquals(
            mapOf("minecraft:spruce_planks" to 64, "minecraft:stone" to 12),
            reviewed.requirements.mapKeys { it.key.id },
        )
        assertTrue(reviewed.excluded.isEmpty())
    }

    @Test
    fun `a struck row survives the swap and is still struck`() {
        // No qty[...] for the stairs — that is exactly what an unchecked box submits.
        val reviewed = succeed(
            form(
                "from" to "oak",
                "to[oak]" to "spruce",
                "row[minecraft:oak_planks]" to "64",
                "qty[minecraft:oak_planks]" to "64",
                "row[minecraft:oak_stairs]" to "8",
            )
        )

        assertEquals(
            mapOf("minecraft:spruce_planks" to 64, "minecraft:spruce_stairs" to 8),
            reviewed.requirements.mapKeys { it.key.id },
        )
        assertEquals(setOf("minecraft:spruce_stairs"), reviewed.excluded)
    }

    @Test
    fun `when a kept row and a struck row merge, the merged row is kept`() {
        // The struck oak planks land on the kept spruce planks. Striking a row the user had
        // checked would be the surprising outcome; the quantity is visible either way.
        val reviewed = succeed(
            form(
                "from" to "oak",
                "to[oak]" to "spruce",
                "row[minecraft:oak_planks]" to "64",
                "row[minecraft:spruce_planks]" to "10",
                "qty[minecraft:spruce_planks]" to "10",
            )
        )

        assertEquals(mapOf("minecraft:spruce_planks" to 74), reviewed.requirements.mapKeys { it.key.id })
        assertTrue(reviewed.excluded.isEmpty(), "the merged row stays checked")
    }

    @Test
    fun `quantities merge rather than one row overwriting the other`() {
        val reviewed = succeed(
            form(
                "from" to "oak",
                "to[oak]" to "spruce",
                "row[minecraft:oak_slab]" to "5",
                "qty[minecraft:oak_slab]" to "5",
                "row[minecraft:spruce_slab]" to "3",
                "qty[minecraft:spruce_slab]" to "3",
            )
        )

        assertEquals(8, reviewed.requirements.values.single())
    }

    @Test
    fun `a missing target is refused`() {
        val result = run(form("from" to "oak", "row[minecraft:oak_planks]" to "1"))

        assertIs<Result.Failure<AppFailure>>(result)
    }

    @Test
    fun `a missing family is refused`() {
        val result = run(form("to[oak]" to "spruce", "row[minecraft:oak_planks]" to "1"))

        assertIs<Result.Failure<AppFailure>>(result)
    }

    @Test
    fun `an id outside the world catalog is refused rather than passed through`() {
        val result = run(
            form(
                "from" to "oak",
                "to[oak]" to "spruce",
                "row[minecraft:not_a_real_item]" to "1",
            )
        )

        assertIs<Result.Failure<AppFailure>>(result)
    }

    @Test
    fun `a non-positive quantity is refused`() {
        val result = run(
            form(
                "from" to "oak",
                "to[oak]" to "spruce",
                "row[minecraft:oak_planks]" to "0",
            )
        )

        assertIs<Result.Failure<AppFailure>>(result)
    }

    @Test
    fun `a form with no rows at all is refused`() {
        val result = run(form("from" to "oak", "to[oak]" to "spruce"))

        assertIs<Result.Failure<AppFailure>>(result)
    }

    @Test
    fun `swapping to a form the target species lacks keeps the row where it is`() {
        // Not reachable from the UI — findSubstitutionFamilies withholds partial targets — but
        // a hand-rolled post must not cost the row.
        val narrowCatalog = catalog + Item("minecraft:oak_sapling", "Oak Sapling")
        val reviewed = runBlocking {
            SubstituteReviewedListStep(narrowCatalog).process(
                form(
                    "from" to "oak",
                    "to[oak]" to "spruce",
                    "row[minecraft:oak_sapling]" to "4",
                    "qty[minecraft:oak_sapling]" to "4",
                )
            )
        }

        assertIs<Result.Success<ReviewedList>>(reviewed)
        assertEquals(mapOf("minecraft:oak_sapling" to 4), reviewed.value.requirements.mapKeys { it.key.id })
    }
}
