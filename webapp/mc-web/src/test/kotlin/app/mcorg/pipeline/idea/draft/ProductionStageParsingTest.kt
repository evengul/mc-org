package app.mcorg.pipeline.idea.draft

import app.mcorg.presentation.templated.idea.createwizard.DraftWizardStage
import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-412 — turning the productions form into draft JSON.
 *
 * The form names its fields `productionMode[i][name]` and `productionRate[i][<item id>]`, and the
 * index only groups them: it is positional, re-assigned on every save, and referenced by nothing.
 * These tests pin what survives that trip, because everything downstream — matching, import,
 * the supply map — reads whatever this produces.
 */
class ProductionStageParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parse(vararg pairs: Pair<String, String>): List<DraftProductionMode> {
        val params: Parameters = parametersOf(*pairs.map { (k, v) -> k to listOf(v) }.toTypedArray())
        val stageJson = buildStageJson(DraftWizardStage.PRODUCTIONS, params)
        return json.decodeFromString(DraftData.serializer(), stageJson).productionModes.orEmpty()
    }

    @Test
    fun `a single unnamed mode keeps its rates and no name`() {
        // The common case: the author never saw the word "mode". The blank name becomes the
        // implicit "Default" when it is stored, not here.
        val modes = parse(
            "productionMode[0][name]" to "",
            "productionRate[0][minecraft:ice]" to "71000",
        )

        assertEquals(1, modes.size)
        assertEquals("", modes.first().name)
        assertEquals(mapOf("minecraft:ice" to 71000), modes.first().rates)
    }

    @Test
    fun `several modes keep their names and their own rates`() {
        val modes = parse(
            "productionMode[0][name]" to "Max speed",
            "productionRate[0][minecraft:ice]" to "62000",
            "productionMode[1][name]" to "Slowed",
            "productionRate[1][minecraft:ice]" to "18000",
        )

        assertEquals(listOf("Max speed", "Slowed"), modes.map { it.name })
        assertEquals(mapOf("minecraft:ice" to 62000), modes[0].rates)
        assertEquals(mapOf("minecraft:ice" to 18000), modes[1].rates)
    }

    @Test
    fun `a mode carrying several items keeps all of them`() {
        val modes = parse(
            "productionMode[0][name]" to "Everything, fast",
            "productionRate[0][minecraft:bone]" to "700",
            "productionRate[0][minecraft:blaze_rod]" to "500",
        )

        assertEquals(
            mapOf("minecraft:bone" to 700, "minecraft:blaze_rod" to 500),
            modes.single().rates,
        )
    }

    @Test
    fun `a named mode with no rates does not survive`() {
        // A named way of running a farm that produces nothing is a form artefact — someone pressed
        // the button and changed their mind — not a fact about the farm.
        val modes = parse(
            "productionMode[0][name]" to "Max speed",
            "productionRate[0][minecraft:ice]" to "62000",
            "productionMode[1][name]" to "Abandoned",
        )

        assertEquals(listOf("Max speed"), modes.map { it.name })
    }

    @Test
    fun `producing nothing is normal and yields no modes`() {
        // A storage system, a base, a decorative build. Not an error.
        assertTrue(parse("productionMode[0][name]" to "").isEmpty())
    }

    @Test
    fun `a blank rate keeps the item as unmeasured output`() {
        // "I know it makes bamboo, I have never timed it" is information worth keeping. Requiring
        // a number would either lose the design or invite an invented one.
        val modes = parse(
            "productionMode[0][name]" to "",
            "productionRate[0][minecraft:bamboo]" to "",
        )

        assertEquals(mapOf("minecraft:bamboo" to null), modes.single().rates)
    }

    @Test
    fun `a non-numeric rate is dropped rather than stored as zero`() {
        val modes = parse(
            "productionMode[0][name]" to "",
            "productionRate[0][minecraft:ice]" to "fast",
            "productionRate[0][minecraft:packed_ice]" to "900",
        )

        assertEquals(mapOf("minecraft:packed_ice" to 900), modes.single().rates)
    }

    @Test
    fun `a negative rate is dropped`() {
        // The column's CHECK would reject it; dropping it here means the form says "that did not
        // stick" instead of the save failing wholesale.
        val modes = parse(
            "productionMode[0][name]" to "",
            "productionRate[0][minecraft:ice]" to "-5",
            "productionRate[0][minecraft:packed_ice]" to "900",
        )

        assertEquals(mapOf("minecraft:packed_ice" to 900), modes.single().rates)
    }

    @Test
    fun `modes stay in the order the author added them`() {
        // Position is the author's ordering and the form's grouping key; index 10 must not sort
        // between 1 and 2.
        val modes = parse(
            "productionMode[0][name]" to "First",
            "productionRate[0][minecraft:ice]" to "1",
            "productionMode[10][name]" to "Eleventh",
            "productionRate[10][minecraft:ice]" to "11",
            "productionMode[2][name]" to "Third",
            "productionRate[2][minecraft:ice]" to "3",
        )

        assertEquals(listOf("First", "Third", "Eleventh"), modes.map { it.name })
    }
}
