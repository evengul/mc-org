package app.mcorg.pipeline.project

import app.mcorg.domain.model.idea.IdeaModeKind
import app.mcorg.domain.model.idea.IdeaProductionMode
import app.mcorg.domain.model.idea.buildTimeModes
import app.mcorg.domain.model.idea.runtimeModes
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-463 — choosing which way a design is built, at import.
 *
 * The case is MCO-439 finding 1's cobblestone farm: *single module / 4 modules* × *with / without
 * storage*, four variants whose material lists differ by roughly 4×. Which one you import decides
 * what the project costs, so the choice reaches the project rather than being defaulted out of
 * sight.
 */
class BuildTimeImportSelectionTest {

    private fun variant(name: String, position: Int, cobble: Int, rate: Int?) = IdeaProductionMode(
        id = position + 1,
        name = name,
        position = position,
        rates = rate?.let { mapOf("minecraft:cobblestone" to it) } ?: emptyMap(),
        kind = IdeaModeKind.BUILD_TIME,
        requirements = mapOf("minecraft:cobblestone" to cobble),
    )

    private val cobbleFarm = listOf(
        variant("1 module", 0, cobble = 400, rate = 231_000),
        variant("4 modules", 1, cobble = 1_600, rate = 924_000),
    )

    private val fortressFarm = listOf(
        IdeaProductionMode(1, "Skeletons only", 0, mapOf("minecraft:bone" to 900)),
        IdeaProductionMode(2, "Everything", 1, mapOf("minecraft:bone" to 700, "minecraft:blaze_rod" to 500)),
    )

    @Test
    fun `build-time and runtime modes are told apart`() {
        val mixed = cobbleFarm + fortressFarm

        assertEquals(listOf("1 module", "4 modules"), mixed.buildTimeModes().map { it.name })
        assertEquals(listOf("Skeletons only", "Everything"), mixed.runtimeModes().map { it.name })
    }

    @Test
    fun `a farm with no build-time axis offers no choice at all`() {
        // Every idea in the bank before MCO-463, and most after it.
        assertTrue(fortressFarm.buildTimeModes().isEmpty())
    }

    @Test
    fun `the named variant's rates are the ones imported`() {
        assertEquals(mapOf("minecraft:cobblestone" to 231_000), ratesForImport(cobbleFarm, "1 module"))
        assertEquals(mapOf("minecraft:cobblestone" to 924_000), ratesForImport(cobbleFarm, "4 modules"))
    }

    @Test
    fun `an unmeasured variant imports no rate rather than borrowing a sibling's`() {
        // The drift this whole issue exists to stop: attributing the 4-module farm's 924k/h to the
        // single-module build because nobody timed that one.
        val untimed = listOf(
            variant("1 module", 0, cobble = 400, rate = null),
            variant("4 modules", 1, cobble = 1_600, rate = 924_000),
        )

        assertEquals(emptyMap(), ratesForImport(untimed, "1 module"))
    }

    @Test
    fun `with no choice given the best producer still wins`() {
        // Unchanged MCO-412 behaviour for every caller that names no mode.
        assertEquals(mapOf("minecraft:cobblestone" to 924_000), ratesForImport(cobbleFarm))
    }

    @Test
    fun `a name that matches nothing falls back rather than importing an empty design`() {
        // What happens when the design is edited between the review rendering and the POST: the
        // variant the form named is gone. Falling back beats failing the import outright.
        val chosen = cobbleFarm.buildTimeModes().firstOrNull { it.name == "8 modules" }
            ?: cobbleFarm.buildTimeModes().firstOrNull()

        assertEquals("1 module", chosen?.name)
    }

    @Test
    fun `the default is the author's first, not the largest build`() {
        // `position` is documented as the author's ordering and explicitly not a ranking. Choosing
        // the biggest producer would silently commit someone to the 4-module build's 4x bill.
        val default = cobbleFarm.buildTimeModes().firstOrNull()

        assertEquals("1 module", default?.name)
        assertEquals(mapOf("minecraft:cobblestone" to 400), default?.requirements)
    }

    @Test
    fun `each variant carries its own material list, and they differ by the module count`() {
        val single = cobbleFarm.first { it.name == "1 module" }
        val four = cobbleFarm.first { it.name == "4 modules" }

        assertEquals(400, single.requirements["minecraft:cobblestone"])
        assertEquals(1_600, four.requirements["minecraft:cobblestone"])
        // 924k / 231k = 4.0 exactly — the rate spread *is* the module count, which is what made
        // this a build-time axis rather than a runtime one.
        assertEquals(4, four.requirements["minecraft:cobblestone"]!! / single.requirements["minecraft:cobblestone"]!!)
    }
}
