package app.mcorg.pipeline.idea.commonsteps

import app.mcorg.domain.model.idea.IdeaModeKind
import app.mcorg.domain.model.idea.IdeaProductionMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCO-412 — the last line of defence before `UNIQUE (idea_id, name)`.
 *
 * The form rejects colliding mode names with a field-level message, but a bodyless publish
 * re-publishes stored draft JSON without re-validating it, so the write path has to survive a
 * collision rather than roll the whole publish back on a 23505.
 */
class IdeaProductionModeMergeTest {

    private fun mode(name: String, vararg rates: Pair<String, Int?>) =
        IdeaProductionModeInput(name, rates.toMap())

    @Test
    fun `a blank name resolves to Default`() {
        val merged = listOf(mode("", "minecraft:ice" to 71000)).mergeByResolvedName()

        assertEquals(IdeaProductionMode.DEFAULT_MODE_NAME, merged.single().name)
    }

    @Test
    fun `two unnamed modes become one Default carrying both items`() {
        val merged = listOf(
            mode("", "minecraft:ice" to 71000),
            mode("", "minecraft:bamboo" to 400),
        ).mergeByResolvedName()

        assertEquals(1, merged.size)
        assertEquals(IdeaProductionMode.DEFAULT_MODE_NAME, merged.single().name)
        assertEquals(mapOf("minecraft:ice" to 71000, "minecraft:bamboo" to 400), merged.single().rates)
    }

    @Test
    fun `the faster rate wins when both name the same item`() {
        // Consistent with bestRateFor, which already answers "how fast, at best".
        val merged = listOf(
            mode("Max speed", "minecraft:ice" to 62000),
            mode("Max speed", "minecraft:ice" to 71000),
        ).mergeByResolvedName()

        assertEquals(mapOf("minecraft:ice" to 71000), merged.single().rates)
    }

    @Test
    fun `a measured rate beats an unmeasured one regardless of order`() {
        // null is "never timed", not zero — it must never displace a real number.
        val nullFirst = listOf(
            mode("Default", "minecraft:ice" to null),
            mode("Default", "minecraft:ice" to 71000),
        ).mergeByResolvedName()
        val numberFirst = listOf(
            mode("Default", "minecraft:ice" to 71000),
            mode("Default", "minecraft:ice" to null),
        ).mergeByResolvedName()

        assertEquals(mapOf("minecraft:ice" to 71000), nullFirst.single().rates)
        assertEquals(mapOf("minecraft:ice" to 71000), numberFirst.single().rates)
    }

    @Test
    fun `an item unmeasured on both sides stays unmeasured rather than becoming zero`() {
        val merged = listOf(
            mode("Default", "minecraft:bamboo" to null),
            mode("Default", "minecraft:bamboo" to null),
        ).mergeByResolvedName()

        assertEquals(mapOf("minecraft:bamboo" to null), merged.single().rates)
    }

    @Test
    fun `distinct names are left alone and keep their order`() {
        val merged = listOf(
            mode("Max speed", "minecraft:ice" to 71000),
            mode("Slowed", "minecraft:ice" to 20000),
        ).mergeByResolvedName()

        assertEquals(listOf("Max speed", "Slowed"), merged.map { it.name })
    }

    @Test
    fun `a trailing space does not create a second mode`() {
        val merged = listOf(
            mode("Max speed", "minecraft:ice" to 71000),
            mode("Max speed ", "minecraft:bamboo" to 400),
        ).mergeByResolvedName()

        assertEquals(1, merged.size)
        assertEquals("Max speed", merged.single().name)
    }

    // -----------------------------------------------------------------------------------------
    // MCO-463 — the two fields a mode gained, and how a name collision resolves them.

    private fun buildTime(name: String, requirements: Map<String, Int>, vararg rates: Pair<String, Int?>) =
        IdeaProductionModeInput(name, rates.toMap(), IdeaModeKind.BUILD_TIME, requirements)

    @Test
    fun `a mode is runtime unless it says otherwise`() {
        // Matches the column default, and what every mode entered before MCO-463 was answering.
        assertEquals(IdeaModeKind.RUNTIME, mode("Max speed", "minecraft:ice" to 71000).kind)
    }

    @Test
    fun `build-time wins a kind collision, because it is the claim with consequences`() {
        // Demoting to runtime would drop the material list on the floor at the next write.
        val buildTimeFirst = listOf(
            buildTime("4 modules", mapOf("minecraft:cobblestone" to 400)),
            mode("4 modules", "minecraft:cobblestone" to 924000),
        ).mergeByResolvedName()
        val runtimeFirst = listOf(
            mode("4 modules", "minecraft:cobblestone" to 924000),
            buildTime("4 modules", mapOf("minecraft:cobblestone" to 400)),
        ).mergeByResolvedName()

        assertEquals(IdeaModeKind.BUILD_TIME, buildTimeFirst.single().kind)
        assertEquals(IdeaModeKind.BUILD_TIME, runtimeFirst.single().kind)
    }

    @Test
    fun `the larger quantity wins when two collide on one material`() {
        // Same argument as rates: under-stating what a build costs is the expensive way to be wrong.
        val merged = listOf(
            buildTime("4 modules", mapOf("minecraft:cobblestone" to 400)),
            buildTime("4 modules", mapOf("minecraft:cobblestone" to 1600)),
        ).mergeByResolvedName()

        assertEquals(mapOf("minecraft:cobblestone" to 1600), merged.single().requirements)
    }

    @Test
    fun `colliding material lists union rather than replace`() {
        val merged = listOf(
            buildTime("With storage", mapOf("minecraft:hopper" to 64)),
            buildTime("With storage", mapOf("minecraft:chest" to 32)),
        ).mergeByResolvedName()

        assertEquals(mapOf("minecraft:hopper" to 64, "minecraft:chest" to 32), merged.single().requirements)
    }

    @Test
    fun `the four cobblestone variants stay four distinct modes`() {
        // MCO-439 finding 1, the case that prompted all of this: single/4 modules x with/without
        // storage. Four names, four material lists, and nothing here collapses them.
        val merged = listOf(
            buildTime("1 module", mapOf("minecraft:cobblestone" to 400), "minecraft:cobblestone" to 231000),
            buildTime("1 module, storage", mapOf("minecraft:cobblestone" to 400, "minecraft:hopper" to 64)),
            buildTime("4 modules", mapOf("minecraft:cobblestone" to 1600), "minecraft:cobblestone" to 924000),
            buildTime("4 modules, storage", mapOf("minecraft:cobblestone" to 1600, "minecraft:hopper" to 256)),
        ).mergeByResolvedName()

        assertEquals(4, merged.size)
        assertEquals(1600, merged[2].requirements["minecraft:cobblestone"])
        assertEquals(256, merged[3].requirements["minecraft:hopper"])
    }
}

/**
 * MCO-463 — a runtime mode does not change what the build cost, so it may not own a material list.
 *
 * The database cannot say this: a CHECK cannot see across to `idea_production_modes.kind`. So it is
 * asserted on the way in, at the one type both write paths construct.
 */
class IdeaModeRequirementsOwnershipTest {

    private val list = mapOf("minecraft:cobblestone" to 1600)

    @Test
    fun `a build-time mode stores the list it was given`() {
        val mode = IdeaProductionModeInput("4 modules", emptyMap(), IdeaModeKind.BUILD_TIME, list)

        assertEquals(list, mode.requirementsToStore)
    }

    @Test
    fun `a runtime mode handed a list stores none of it`() {
        // Dropped rather than rejected: the caller is wrong, but a publish is not worth failing
        // over a field that carries no meaning for this kind of mode.
        val mode = IdeaProductionModeInput("Max speed", emptyMap(), IdeaModeKind.RUNTIME, list)

        assertEquals(emptyMap(), mode.requirementsToStore)
    }

    @Test
    fun `build-time lists replace the idea's base list`() {
        val modes = listOf(IdeaProductionModeInput("4 modules", emptyMap(), IdeaModeKind.BUILD_TIME, list))

        assertTrue(modes.replaceBaseRequirements())
    }

    @Test
    fun `runtime modes leave the base list alone`() {
        // The pre-MCO-463 shape, and every idea in the bank: one list, hanging off the idea.
        val modes = listOf(IdeaProductionModeInput("Max speed", mapOf("minecraft:ice" to 71000)))

        assertFalse(modes.replaceBaseRequirements())
    }

    @Test
    fun `a runtime mode carrying a stray list does not displace the base list`() {
        // replaceBaseRequirements asks what will be *stored*, not what was passed — otherwise a
        // caller error would silently delete the idea's real material list.
        val modes = listOf(IdeaProductionModeInput("Max speed", emptyMap(), IdeaModeKind.RUNTIME, list))

        assertFalse(modes.replaceBaseRequirements())
    }
}
