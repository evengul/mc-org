package app.mcorg.pipeline.resources

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * MCO-316 — what decides whether stored demand can be trusted.
 *
 * The fingerprint is the whole reason the materialised view is safe to read: re-running the
 * planner is expensive, hashing its inputs is not. A fingerprint that missed an input would
 * serve stale demand forever; one that changed spuriously would re-derive on every page load.
 */
class DemandFingerprintTest {

    private fun fingerprint(
        version: String = "1.21.4",
        targets: List<Triple<String, Long, String?>> = listOf(Triple("minecraft:hopper", 5630L, null)),
        supplied: Map<String, String> = emptyMap(),
        overrides: List<Pair<String, String>> = emptyList(),
    ) = DemandFingerprint.of(version, targets, supplied, overrides)

    @Test
    fun `the same inputs give the same fingerprint`() {
        assertEquals(fingerprint(), fingerprint())
    }

    @Test
    fun `input order does not matter`() {
        // The targets arrive from a query with no ORDER BY, so two loads of unchanged data must
        // not look like a change.
        val a = fingerprint(
            targets = listOf(Triple("minecraft:hopper", 5630L, null), Triple("minecraft:chest", 128L, null)),
        )
        val b = fingerprint(
            targets = listOf(Triple("minecraft:chest", 128L, null), Triple("minecraft:hopper", 5630L, null)),
        )
        assertEquals(a, b)
    }

    @Test
    fun `a changed quantity changes the fingerprint`() {
        assertNotEquals(
            fingerprint(targets = listOf(Triple("minecraft:hopper", 5630L, null))),
            fingerprint(targets = listOf(Triple("minecraft:hopper", 5631L, null))),
        )
    }

    @Test
    fun `an added target changes the fingerprint`() {
        assertNotEquals(
            fingerprint(),
            fingerprint(
                targets = listOf(Triple("minecraft:hopper", 5630L, null), Triple("minecraft:chest", 1L, null)),
            ),
        )
    }

    @Test
    fun `a farm coming online changes the fingerprint`() {
        // The case that makes this more than a per-project hash: marking a farm DONE stops every
        // other project's chain expanding past the item it supplies, so their stored demand is
        // stale even though nothing about those projects changed.
        assertNotEquals(
            fingerprint(supplied = emptyMap()),
            fingerprint(supplied = mapOf("minecraft:iron_ingot" to "Farm(Iron Farm)")),
        )
    }

    @Test
    fun `a source pin changes the fingerprint`() {
        assertNotEquals(
            fingerprint(),
            fingerprint(overrides = listOf("src:minecraft:iron_ingot" to "smelting")),
        )
    }

    @Test
    fun `a new Minecraft version changes the fingerprint`() {
        // The item-source graph is version-keyed, so the same targets can plan differently.
        assertNotEquals(fingerprint(version = "1.21.4"), fingerprint(version = "1.21.5"))
    }

    @Test
    fun `the fingerprint is a fixed-length hex digest`() {
        val value = fingerprint().value
        assertEquals(64, value.length)
        assertEquals(true, value.all { it in "0123456789abcdef" })
    }
}
