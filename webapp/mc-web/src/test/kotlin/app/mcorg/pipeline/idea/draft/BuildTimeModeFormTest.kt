package app.mcorg.pipeline.idea.draft

import app.mcorg.domain.model.idea.IdeaModeKind
import app.mcorg.presentation.templated.idea.createwizard.DraftWizardStage
import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-463 — what the create form submits for a build-time variant, and what the draft keeps.
 *
 * The four-variant cobblestone farm from MCO-439 finding 1 is the case throughout: *single module /
 * 4 modules* × *with / without storage*, each with its own `.litematic` and so its own material
 * list. Before this the form could only hold one list for the whole idea, so three of the four were
 * wrong.
 */
class BuildTimeModeFormTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun productions(vararg pairs: Pair<String, String>): List<DraftProductionMode> {
        val params: Parameters = parametersOf(*pairs.map { (k, v) -> k to listOf(v) }.toTypedArray())
        val stageJson = buildStageJson(DraftWizardStage.PRODUCTIONS, params)
        return json.decodeFromString(DraftData.serializer(), stageJson).productionModes.orEmpty()
    }

    @Test
    fun `a mode with no kind submitted is a runtime mode`() {
        // Every draft saved before this field existed, and every mode the first button adds.
        val modes = productions(
            "productionMode[0][name]" to "Max speed",
            "productionRate[0][minecraft:ice]" to "71000",
        )

        assertEquals(IdeaModeKind.RUNTIME, modes.single().kind)
        assertTrue(modes.single().requirements.isEmpty())
    }

    @Test
    fun `a build-time variant keeps its own material list`() {
        val modes = productions(
            "productionMode[0][name]" to "4 modules",
            "productionMode[0][kind]" to "BUILD_TIME",
            "productionRate[0][minecraft:cobblestone]" to "924000",
            "modeRequirements[0][minecraft:cobblestone]" to "1600",
            "modeRequirements[0][minecraft:hopper]" to "256",
        )

        val mode = modes.single()
        assertEquals(IdeaModeKind.BUILD_TIME, mode.kind)
        assertEquals(mapOf("minecraft:cobblestone" to 1600, "minecraft:hopper" to 256), mode.requirements)
    }

    @Test
    fun `a build-time variant survives on its material list with no rate at all`() {
        // The rule before MCO-463 was "no rates, no mode". A variant whose output nobody timed is
        // still a real variant — it says what building it costs, which is the half that matters at
        // import — so dropping it here would silently discard one of the four.
        val modes = productions(
            "productionMode[0][name]" to "1 module, storage",
            "productionMode[0][kind]" to "BUILD_TIME",
            "modeRequirements[0][minecraft:cobblestone]" to "400",
        )

        assertEquals(1, modes.size)
        assertEquals("1 module, storage", modes.single().name)
        assertEquals(mapOf("minecraft:cobblestone" to 400), modes.single().requirements)
    }

    @Test
    fun `a runtime mode with no rates still says nothing and does not survive`() {
        // The MCO-412 rule, unchanged for the kind it was written about.
        val modes = productions("productionMode[0][name]" to "Max speed")

        assertTrue(modes.isEmpty())
    }

    @Test
    fun `a runtime mode carrying a stray material list has it dropped`() {
        // Switching a block back to runtime should not leave materials it no longer claims — they
        // would reappear on reload and then be dropped again at the write path, silently.
        val modes = productions(
            "productionMode[0][name]" to "Max speed",
            "productionMode[0][kind]" to "RUNTIME",
            "productionRate[0][minecraft:ice]" to "71000",
            "modeRequirements[0][minecraft:packed_ice]" to "900",
        )

        assertTrue(modes.single().requirements.isEmpty())
    }

    @Test
    fun `the four cobblestone variants each keep their own list`() {
        val modes = productions(
            "productionMode[0][name]" to "1 module",
            "productionMode[0][kind]" to "BUILD_TIME",
            "productionRate[0][minecraft:cobblestone]" to "231000",
            "modeRequirements[0][minecraft:cobblestone]" to "400",
            "productionMode[1][name]" to "1 module, storage",
            "productionMode[1][kind]" to "BUILD_TIME",
            "modeRequirements[1][minecraft:cobblestone]" to "400",
            "modeRequirements[1][minecraft:hopper]" to "64",
            "productionMode[2][name]" to "4 modules",
            "productionMode[2][kind]" to "BUILD_TIME",
            "productionRate[2][minecraft:cobblestone]" to "924000",
            "modeRequirements[2][minecraft:cobblestone]" to "1600",
            "productionMode[3][name]" to "4 modules, storage",
            "productionMode[3][kind]" to "BUILD_TIME",
            "modeRequirements[3][minecraft:cobblestone]" to "1600",
            "modeRequirements[3][minecraft:hopper]" to "256",
        )

        assertEquals(4, modes.size)
        assertEquals(
            listOf("1 module", "1 module, storage", "4 modules", "4 modules, storage"),
            modes.map { it.name },
        )
        // The 4x that made this a build-time axis in the first place, held per variant.
        assertEquals(400, modes[0].requirements["minecraft:cobblestone"])
        assertEquals(1600, modes[2].requirements["minecraft:cobblestone"])
        assertEquals(64, modes[1].requirements["minecraft:hopper"])
        assertEquals(256, modes[3].requirements["minecraft:hopper"])
    }

    @Test
    fun `a material row with no usable quantity is dropped rather than stored as zero`() {
        // Unlike a rate, where blank means "never measured", a blank quantity says nothing at all —
        // "some cobblestone" is not a material list.
        val modes = productions(
            "productionMode[0][name]" to "4 modules",
            "productionMode[0][kind]" to "BUILD_TIME",
            "modeRequirements[0][minecraft:cobblestone]" to "1600",
            "modeRequirements[0][minecraft:hopper]" to "",
            "modeRequirements[0][minecraft:chest]" to "nonsense",
            "modeRequirements[0][minecraft:torch]" to "0",
        )

        assertEquals(mapOf("minecraft:cobblestone" to 1600), modes.single().requirements)
    }

    @Test
    fun `an unrecognised kind falls back to runtime rather than failing the save`() {
        // The field is a hidden input, so a hand-edited value is the only way here. Losing the
        // whole draft over it would be worse than treating it as the unmarked default.
        val modes = productions(
            "productionMode[0][name]" to "Max speed",
            "productionMode[0][kind]" to "SOMETHING_ELSE",
            "productionRate[0][minecraft:ice]" to "71000",
        )

        assertEquals(IdeaModeKind.RUNTIME, modes.single().kind)
    }
}
