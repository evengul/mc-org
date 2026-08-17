package app.mcorg.pipeline.idea.commonsteps

import app.mcorg.domain.model.idea.IdeaProductionMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
}
