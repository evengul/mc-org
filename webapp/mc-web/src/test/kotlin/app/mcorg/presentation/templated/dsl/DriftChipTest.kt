package app.mcorg.presentation.templated.dsl

import app.mcorg.pipeline.resources.MeasuredStock
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the drift chip appears, and when it must not (MCO-539).
 *
 * The "must not" cases carry the weight. A chip on every row of a half-tagged project is worse
 * than no chip at all: it would read as a hundred discrepancies when the only fact is that
 * tagging is not finished, and it would push someone toward adopting numbers that are wrong.
 */
class DriftChipTest {

    private fun render(current: Int, measured: MeasuredStock?): String =
        createHTML().div {
            resourceRow(
                id = 1,
                worldId = 1,
                projectId = 2,
                itemName = "Iron Ingot",
                current = current,
                required = 512,
                measured = measured,
            )
        }

    private fun stock(measured: Long, containers: Int = 4) =
        MeasuredStock(measured, containers, Instant.now())

    @Test
    fun `a row with no tagged containers shows no chip`() {
        // The common case on a project nobody has tagged yet. Without this guard every row would
        // claim a drift of minus-everything, which is not a measurement — it is the absence of one.
        val html = render(current = 500, measured = null)

        assertFalse(html.contains("resource-row__drift"), html)
        assertFalse(html.contains("Adopt"), html)
    }

    @Test
    fun `a measurement from zero containers is not a measurement`() {
        // A rollup row can survive its containers being untagged. Zero containers means nobody is
        // reporting this item, so "0" is silence rather than evidence of an empty base.
        val html = render(current = 500, measured = stock(0, containers = 0))

        assertFalse(html.contains("resource-row__drift"), html)
    }

    @Test
    fun `agreement shows no chip, because there is no disagreement to show`() {
        val html = render(current = 12, measured = stock(12))

        assertFalse(html.contains("resource-row__drift"), html)
        assertFalse(html.contains(">Adopt<"), html)
    }

    @Test
    fun `less in the chests than was typed reads as negative, the expected state while tagging`() {
        val html = render(current = 500, measured = stock(12))

        assertTrue(html.contains("resource-row__drift--under"), html)
        assertTrue(html.contains("12 in 4 chests"), html)
        // The sign is in the text, not only in the colour — colour alone fails a colour-blind
        // reader, and this is the signal the whole chip exists to carry.
        assertTrue(html.contains("-488"), html)
    }

    @Test
    fun `more in the chests than was typed reads as positive`() {
        val html = render(current = 100, measured = stock(140))

        assertTrue(html.contains("resource-row__drift--over"), html)
        assertTrue(html.contains("+40"), html)
    }

    @Test
    fun `one container is a chest, not chests`() {
        val html = render(current = 5, measured = stock(1, containers = 1))

        assertTrue(html.contains("1 in 1 chest<"), html)
    }

    @Test
    fun `the chip offers adopt, and says what adopting would set`() {
        val html = render(current = 500, measured = stock(12))

        assertTrue(html.contains("/resources/gathering/1/adopt"), html)
        // The label is "Adopt"; the accessible name has to carry the number, or a screen reader
        // hears a button that could do anything.
        assertTrue(html.contains("Set Iron Ingot to 12"), html)
    }

    @Test
    fun `the tooltip says how old the oldest reading is, which is how much to trust it`() {
        val html = render(
            current = 500,
            measured = MeasuredStock(12, 4, Instant.now().minusSeconds(3 * 60 * 60)),
        )

        // The oldest, not the newest: one chest nobody has walked past in days is exactly what
        // makes a total stale, and anything but the oldest would hide it.
        assertTrue(html.contains("3h ago"), html)
    }
}
