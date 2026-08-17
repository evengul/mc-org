package app.mcorg.pipeline.idea.validators

import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-412 — what the productions stage rejects.
 *
 * Both cases here used to be silent in their own way: colliding mode names reached
 * `UNIQUE (idea_id, name)` and rolled the publish back with no field-level message, and an item id
 * outside the catalog stored happily and then never matched anything (MCO-294) while making the
 * idea unimportable into every world.
 */
class ValidateIdeaProductionsStepTest {

    private val catalog = setOf("minecraft:ice", "minecraft:blue_ice", "minecraft:bamboo")

    private fun validate(vararg pairs: Pair<String, String>): List<ValidationFailure> {
        val params: Parameters = parametersOf(*pairs.map { (k, v) -> k to listOf(v) }.toTypedArray())
        val step = ValidateIdeaProductionsStep(knownItemIds = { catalog })
        return runBlocking { (step.process(params) as Result.Success).value }
    }

    @Test
    fun `the ordinary single unnamed mode passes`() {
        val errors = validate(
            "productionMode[0][name]" to "",
            "productionRate[0][minecraft:ice]" to "71000",
        )

        assertTrue(errors.isEmpty(), "a farm with one mode and a known item is the common case")
    }

    @Test
    fun `two unnamed modes are rejected before they collide on Default`() {
        // Both resolve to "Default", which the unique index refuses. The author sees a name field
        // on each block at this point, so asking them to fill one in is actionable.
        val errors = validate(
            "productionMode[0][name]" to "",
            "productionRate[0][minecraft:ice]" to "71000",
            "productionMode[1][name]" to "",
            "productionRate[1][minecraft:ice]" to "62000",
        )

        assertEquals(1, errors.size)
        val error = errors.single() as ValidationFailure.CustomValidation
        assertTrue(error.parameterName.startsWith("productionMode["))
        assertTrue(error.message.contains("unnamed"), "should say what is wrong: ${error.message}")
    }

    @Test
    fun `two modes with the same name are rejected and the name is quoted back`() {
        val errors = validate(
            "productionMode[0][name]" to "Max speed",
            "productionRate[0][minecraft:ice]" to "71000",
            "productionMode[1][name]" to "Max speed",
            "productionRate[1][minecraft:bamboo]" to "400",
        )

        val error = errors.single() as ValidationFailure.CustomValidation
        assertTrue(error.message.contains("Max speed"), "the message names the collision: ${error.message}")
    }

    @Test
    fun `names differing only by surrounding space still collide`() {
        // They collide in the database, which trims nothing but receives the resolved name — so
        // the check has to trim in the same place the write does.
        val errors = validate(
            "productionMode[0][name]" to "Max speed",
            "productionRate[0][minecraft:ice]" to "71000",
            "productionMode[1][name]" to "  Max speed  ",
            "productionRate[1][minecraft:bamboo]" to "400",
        )

        assertEquals(1, errors.size)
    }

    @Test
    fun `distinct names are fine`() {
        val errors = validate(
            "productionMode[0][name]" to "Max speed",
            "productionRate[0][minecraft:ice]" to "71000",
            "productionMode[1][name]" to "Slowed",
            "productionRate[1][minecraft:ice]" to "20000",
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `an item id outside the catalog is rejected and named`() {
        // "Blue Ice" typed into the free-text field becomes minecraft:Blue Ice client-side.
        val errors = validate(
            "productionMode[0][name]" to "",
            "productionRate[0][minecraft:Blue Ice]" to "3000",
        )

        val error = errors.single() as ValidationFailure.CustomValidation
        assertEquals("productionRate[0][minecraft:Blue Ice]", error.parameterName)
        assertTrue(error.message.contains("minecraft:Blue Ice"), "the message quotes the id: ${error.message}")
    }

    @Test
    fun `an unmeasured rate is still checked for a real item`() {
        // A blank rate is legitimate (MCO-412), but the item it names still has to exist.
        val errors = validate(
            "productionMode[0][name]" to "",
            "productionRate[0][minecraft:bamboo]" to "",
            "productionRate[0][minecraft:nonsense]" to "",
        )

        assertEquals(1, errors.size)
        assertTrue((errors.single() as ValidationFailure.CustomValidation).message.contains("minecraft:nonsense"))
    }

    @Test
    fun `an unreadable catalog rejects nothing`() {
        // A database blip is not evidence the author is wrong, and blocking publish on it would
        // lose the whole form.
        val params = parametersOf("productionRate[0][minecraft:whatever]" to listOf("10"))
        val step = ValidateIdeaProductionsStep(knownItemIds = { null })
        val errors = runBlocking { (step.process(params) as Result.Success).value }

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `an idea that produces nothing is not an error`() {
        val errors = validate("productionMode[0][name]" to "")

        assertTrue(errors.isEmpty(), "most builds produce nothing; that is the normal case")
    }
}
