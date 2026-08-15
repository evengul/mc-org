package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.pipeline.Result
import io.ktor.http.parametersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Reading the review screen's submission back — with the repeated ids that section grouping
 * (MCO-398) makes routine.
 */
class ValidateReviewedMaterialsStepTest {

    private val catalog = listOf("minecraft:oak_planks", "minecraft:glass", "minecraft:hopper")
        .map { Item(it, it.substringAfterLast(':')) }

    private fun submit(vararg rows: ReviewedMaterial): Result<*, SchematicProject> = runBlocking {
        ValidateReviewedMaterialsStep(catalog).process(
            parametersOf(
                "name" to listOf("Build"),
                ReviewedMaterialsCodec.FIELD to listOf(ReviewedMaterialsCodec.encode(rows.toList())),
            )
        )
    }

    private fun row(id: String, amount: Int, included: Boolean = true) =
        ReviewedMaterial("minecraft:$id", amount, included)

    @Test
    fun `the same item in two sections is summed, not overwritten`() {
        // The bug this guards: `requirements[item] = row.amount` silently let the shell's 200
        // planks replace the frame's 500. Nothing would have reported the missing 500 — the
        // same shape of quiet loss MCO-315 was about.
        val result = submit(row("oak_planks", 500), row("oak_planks", 200))
        assertIs<Result.Success<SchematicProject>>(result)

        assertEquals(
            mapOf("minecraft:oak_planks" to 700),
            result.value.requirements.mapKeys { it.key.id },
        )
    }

    @Test
    fun `a struck row contributes nothing to the sum`() {
        // Striking the shell's section must remove its contribution and keep the frame's.
        val result = submit(row("oak_planks", 500), row("oak_planks", 200, included = false))
        assertIs<Result.Success<SchematicProject>>(result)

        assertEquals(500, result.value.requirements.values.single())
    }

    @Test
    fun `distinct items across sections all survive`() {
        val result = submit(row("oak_planks", 500), row("glass", 4000), row("hopper", 40))
        assertIs<Result.Success<SchematicProject>>(result)

        assertEquals(
            mapOf("minecraft:oak_planks" to 500, "minecraft:glass" to 4000, "minecraft:hopper" to 40),
            result.value.requirements.mapKeys { it.key.id },
        )
    }

    @Test
    fun `striking every section is refused rather than creating an empty project`() {
        val result = submit(row("oak_planks", 500, included = false), row("glass", 40, included = false))

        assertTrue(result is Result.Failure)
    }

    @Test
    fun `an id outside the world catalog is refused`() {
        val result = submit(ReviewedMaterial("minecraft:not_a_thing", 5, true))

        assertTrue(result is Result.Failure)
    }
}
