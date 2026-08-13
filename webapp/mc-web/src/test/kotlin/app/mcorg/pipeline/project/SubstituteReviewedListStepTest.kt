package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * MCO-304 — reading the review form back, one family swap at a time.
 *
 * Since MCO-315 the form sends the list as one `materials` field rather than two parameters
 * per row; the struck rows ride along inside it, which is what keeps them struck across a swap.
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

    /** `id to amount` for a kept row, `"!" + id to amount` for a struck one. */
    private fun materials(vararg rows: Pair<String, Int>): String =
        ReviewedMaterialsCodec.encode(
            rows.map { (id, amount) ->
                ReviewedMaterial(id.removePrefix("!"), amount, included = !id.startsWith("!"))
            }
        )

    private fun form(vararg pairs: Pair<String, String>) = Parameters.build {
        pairs.forEach { (key, value) -> append(key, value) }
    }

    private fun swap(from: String, to: String, materials: String) =
        form("from" to from, "to" to to, ReviewedMaterialsCodec.FIELD to materials)

    private fun run(params: Parameters) = runBlocking { step.process(params) }

    private fun succeed(params: Parameters): ReviewedList {
        val result = run(params)
        assertIs<Result.Success<ReviewedList>>(result, "expected success, got $result")
        return result.value
    }

    @Test
    fun `every row in the family moves and everything else stays`() {
        val reviewed = succeed(
            swap("oak", "spruce", materials("minecraft:oak_planks" to 64, "minecraft:stone" to 12))
        )

        assertEquals(
            mapOf("minecraft:spruce_planks" to 64, "minecraft:stone" to 12),
            reviewed.requirements.mapKeys { it.key.id },
        )
        assertTrue(reviewed.excluded.isEmpty())
    }

    @Test
    fun `a struck row survives the swap and is still struck`() {
        val reviewed = succeed(
            swap("oak", "spruce", materials("minecraft:oak_planks" to 64, "!minecraft:oak_stairs" to 8))
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
            swap("oak", "spruce", materials("!minecraft:oak_planks" to 64, "minecraft:spruce_planks" to 10))
        )

        assertEquals(mapOf("minecraft:spruce_planks" to 74), reviewed.requirements.mapKeys { it.key.id })
        assertTrue(reviewed.excluded.isEmpty(), "the merged row stays checked")
    }

    @Test
    fun `quantities merge rather than one row overwriting the other`() {
        val reviewed = succeed(
            swap("oak", "spruce", materials("minecraft:oak_slab" to 5, "minecraft:spruce_slab" to 3))
        )

        assertEquals(8, reviewed.requirements.values.single())
    }

    @Test
    fun `a list far past the old parameter cap swaps whole, struck rows included`() {
        // MCO-315: at two parameters per row this list lost its tail on the way in, so a swap
        // silently deleted everything past ~row 466 of the review screen.
        val bulk = (1..300).map { Item("minecraft:bulk_$it", "Bulk $it") }
        val wide = SubstituteReviewedListStep(catalog + bulk)
        val rows = buildList {
            add("minecraft:oak_planks" to 64)
            add("!minecraft:oak_stairs" to 8)
            bulk.forEachIndexed { index, item ->
                add((if (index % 5 == 0) "!" else "") + item.id to index + 1)
            }
        }

        val result = runBlocking {
            wide.process(swap("oak", "spruce", materials(*rows.toTypedArray())))
        }

        assertIs<Result.Success<ReviewedList>>(result, "expected success, got $result")
        val reviewed = result.value
        assertEquals(302, reviewed.requirements.size, "every row survives the round-trip")
        assertEquals(64, reviewed.requirements.entries.single { it.key.id == "minecraft:spruce_planks" }.value)
        assertEquals(
            bulk.filterIndexed { index, _ -> index % 5 == 0 }.map { it.id }.toSet() +
                setOf("minecraft:spruce_stairs"),
            reviewed.excluded,
            "the struck rows are still struck, all the way to the end of the list",
        )
    }

    @Test
    fun `a truncated list is refused rather than swapped in part`() {
        // Every id here is in the catalog, so truncation is the only thing that can fail.
        val bulk = (1..600).map { Item("minecraft:bulk_$it", "Bulk $it") }
        val wide = SubstituteReviewedListStep(catalog + bulk)
        val full = materials(*bulk.mapIndexed { index, item -> item.id to index + 1 }.toTypedArray())
        val truncated = full.split(";").take(2 + 466).joinToString(";")

        val result = runBlocking { wide.process(swap("oak", "spruce", truncated)) }

        assertIs<Result.Failure<AppFailure>>(result)
        val error = result.error
        assertIs<AppFailure.ValidationError>(error)
        assertTrue(
            error.errors.filterIsInstance<ValidationFailure.CustomValidation>()
                .any { "466 of 600" in it.message },
            "the user is told the list arrived short, not handed a partial swap: ${error.errors}",
        )
    }

    @Test
    fun `a missing target is refused`() {
        val result = run(
            form("from" to "oak", ReviewedMaterialsCodec.FIELD to materials("minecraft:oak_planks" to 1))
        )

        assertIs<Result.Failure<AppFailure>>(result)
    }

    @Test
    fun `a missing family is refused`() {
        val result = run(
            form("to" to "spruce", ReviewedMaterialsCodec.FIELD to materials("minecraft:oak_planks" to 1))
        )

        assertIs<Result.Failure<AppFailure>>(result)
    }

    @Test
    fun `an id outside the world catalog is refused rather than passed through`() {
        val result = run(swap("oak", "spruce", materials("minecraft:not_a_real_item" to 1)))

        assertIs<Result.Failure<AppFailure>>(result)
    }

    @Test
    fun `a non-positive quantity is refused`() {
        val result = run(swap("oak", "spruce", "v1;1;minecraft:oak_planks=0"))

        assertIs<Result.Failure<AppFailure>>(result)
    }

    @Test
    fun `a form with no rows at all is refused`() {
        assertIs<Result.Failure<AppFailure>>(run(swap("oak", "spruce", materials())))
    }

    @Test
    fun `a form with no material list at all is refused`() {
        assertIs<Result.Failure<AppFailure>>(run(form("from" to "oak", "to" to "spruce")))
    }

    @Test
    fun `swapping to a form the target species lacks keeps the row where it is`() {
        // Not reachable from the UI — findSubstitutionFamilies withholds partial targets — but
        // a hand-rolled post must not cost the row.
        val narrowCatalog = catalog + Item("minecraft:oak_sapling", "Oak Sapling")
        val reviewed = runBlocking {
            SubstituteReviewedListStep(narrowCatalog).process(
                swap("oak", "spruce", materials("minecraft:oak_sapling" to 4))
            )
        }

        assertIs<Result.Success<ReviewedList>>(reviewed)
        assertEquals(mapOf("minecraft:oak_sapling" to 4), reviewed.value.requirements.mapKeys { it.key.id })
    }
}
