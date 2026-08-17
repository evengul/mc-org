package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.pipeline.project.ResolvedRegion
import app.mcorg.test.fixtures.TestDataFactory
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCO-398 — the import review's section grouping, rendered.
 *
 * These live here rather than in an IT because no real `.litematic` fixture has more than one
 * region (every file in `mc-nbt/src/test/resources/litematica` is single-region) and `mc-nbt`
 * is parse-only, so there is no way to synthesise a multi-region upload. The grouping is
 * therefore exercised where its input can be constructed directly.
 */
class ImportReviewPageTest {

    private val user = TestDataFactory.createTestTokenProfile()

    private fun item(id: String) = Item("minecraft:$id", id.replaceFirstChar { it.uppercase() })

    private fun render(
        requirements: Map<Item, Int>,
        regions: List<ResolvedRegion> = emptyList(),
        placedCounts: Map<String, Int> = emptyMap(),
    ) = importReviewPage(
        user = user,
        worldId = 1,
        worldName = "World",
        projectName = "Build",
        requirements = requirements,
        placedCounts = placedCounts,
        regions = regions,
    )

    private val frame = ResolvedRegion(
        name = "Functional frame",
        requirements = listOf(item("oak_planks") to 500, item("hopper") to 40),
    )
    private val shell = ResolvedRegion(
        name = "Display glass",
        requirements = listOf(item("glass") to 4000, item("oak_planks") to 200),
    )

    @Test
    fun `two regions render as two named sections with a header checkbox each`() {
        val html = render(
            requirements = mapOf(item("glass") to 4000, item("oak_planks") to 700, item("hopper") to 40),
            regions = listOf(frame, shell),
        )

        assertContains(html, "import-review__region")
        assertContains(html, "Functional frame")
        assertContains(html, "Display glass")
        assertEquals(
            2,
            Regex("import-review__region-include").findAll(html).count(),
            "one include-everything box per section",
        )
    }

    @Test
    fun `the largest section comes first`() {
        // A decorative shell is usually the biggest thing in the file and the one you strike,
        // so it should not be buried under the section you are definitely building.
        val html = render(
            requirements = mapOf(item("glass") to 4000, item("oak_planks") to 700, item("hopper") to 40),
            regions = listOf(frame, shell),
        )

        assertTrue(
            html.indexOf("Display glass") < html.indexOf("Functional frame"),
            "4,200 blocks of shell should sort above 540 of frame",
        )
    }

    @Test
    fun `an item in two sections renders once per section`() {
        val html = render(
            requirements = mapOf(item("oak_planks") to 700),
            regions = listOf(frame, shell),
        )

        assertEquals(
            2,
            Regex("""data-item-id="minecraft:oak_planks"""").findAll(html).count(),
            "500 in the frame and 200 in the shell are two separate decisions",
        )
        // The two rows must not collide on a DOM id, or both labels would target one box.
        assertContains(html, """id="include-0-minecraft-oak-planks"""")
        assertContains(html, """id="include-1-minecraft-oak-planks"""")
    }

    @Test
    fun `the materials field carries one row per rendered checkbox`() {
        val html = render(
            requirements = mapOf(item("glass") to 4000, item("oak_planks") to 700, item("hopper") to 40),
            regions = listOf(frame, shell),
        )

        // 4 rows across the two sections, not 3 distinct items — MCO-315's declared count has
        // to match the checkboxes or the server rejects the submission as truncated.
        assertContains(html, """value="v1;4;""")
    }

    @Test
    fun `the count reports distinct materials, not rows`() {
        val html = render(
            requirements = mapOf(item("glass") to 4000, item("oak_planks") to 700, item("hopper") to 40),
            regions = listOf(frame, shell),
        )

        assertContains(html, "3 items in 2 sections")
    }

    @Test
    fun `each section carries a worded expand control, not just a chevron`() {
        val html = render(
            requirements = mapOf(item("glass") to 4000, item("oak_planks") to 700),
            regions = listOf(frame, shell),
        )

        assertContains(html, "Show materials")
        assertContains(html, "Hide materials")
        assertEquals(
            2,
            Regex("import-review__region-toggle--closed").findAll(html).count(),
            "one expand control per section",
        )
    }

    @Test
    fun `grouped lists say what the sections are`() {
        // Subregions are a Litematica concept, not a Seam one — a stack of collapsed bars
        // explains neither what they are nor that they open.
        val html = render(
            requirements = mapOf(item("glass") to 4000, item("oak_planks") to 700),
            regions = listOf(frame, shell),
        )

        assertContains(html, "built from 2 sections")
    }

    @Test
    fun `an ungrouped list gets no sections explainer`() {
        val html = render(requirements = mapOf(item("oak_planks") to 500))

        assertFalse(html.contains("import-review__sections-lead"))
    }

    @Test
    fun `a single region renders with no section chrome at all`() {
        // Litematica names a lone region after the schematic, or leaves it "Unnamed" — every
        // real fixture does one or the other, so a header would wrap the whole list in noise.
        val html = render(
            requirements = mapOf(item("oak_planks") to 500),
            regions = listOf(ResolvedRegion("WiskeProSorter", listOf(item("oak_planks") to 500))),
        )

        assertFalse(html.contains("import-review__region"), "no section wrapper")
        assertFalse(html.contains("WiskeProSorter"), "and no header repeating the schematic name")
        assertContains(html, "1 item")
    }

    @Test
    fun `a schematic with no regions renders exactly as before`() {
        val html = render(requirements = mapOf(item("oak_planks") to 500, item("hopper") to 40))

        assertFalse(html.contains("import-review__region"))
        assertContains(html, "2 items")
        assertContains(html, """value="v1;2;""")
    }

    @Test
    fun `an empty region is never offered as a section`() {
        val html = render(
            requirements = mapOf(item("oak_planks") to 500),
            regions = listOf(frame, ResolvedRegion("Air pocket", emptyList())),
        )

        assertFalse(html.contains("Air pocket"))
    }

    // --- Several files as one import (MCO-414) ---

    @Test
    fun `a file contributing one section is named by the file, not its region`() {
        // Litematica names a lone region after the schematic, so showing both would read as
        // "Sorter (nether) — Sorter (nether)". The file name is the part the user chose.
        val html = render(
            requirements = mapOf(item("oak_planks") to 700),
            regions = listOf(
                ResolvedRegion("Sorter", listOf(item("oak_planks") to 500), sourceFile = "Sorter"),
                ResolvedRegion("Sorter (nether)", listOf(item("oak_planks") to 200), sourceFile = "Sorter (nether)"),
            ),
        )

        assertContains(html, "Sorter (nether)")
        assertFalse(
            html.contains("Sorter (nether) — Sorter (nether)"),
            "the file should not be printed twice",
        )
    }

    @Test
    fun `regions sharing a name across files are told apart by their file`() {
        // The collision the issue calls out: two files can each hold a region called Main, and
        // "Main" twice in the section list is unreadable.
        val html = render(
            requirements = mapOf(item("oak_planks") to 300, item("glass") to 90),
            regions = listOf(
                ResolvedRegion("Main", listOf(item("oak_planks") to 200), sourceFile = "Overworld"),
                ResolvedRegion("Shell", listOf(item("oak_planks") to 100), sourceFile = "Overworld"),
                ResolvedRegion("Main", listOf(item("glass") to 90), sourceFile = "Nether"),
            ),
        )

        assertContains(html, "Overworld — Main")
        assertContains(html, "Overworld — Shell")
        // Nether contributed one section, so it is named by the file alone.
        assertContains(html, "Nether")
    }

    @Test
    fun `the lead says how many files were imported`() {
        // "3 sections" alone reads the same whether the nether file arrived or was silently
        // dropped; naming the file count is the confirmation that both halves are here.
        val html = render(
            requirements = mapOf(item("oak_planks") to 700),
            regions = listOf(
                ResolvedRegion("Sorter", listOf(item("oak_planks") to 500), sourceFile = "Overworld"),
                ResolvedRegion("Sorter", listOf(item("oak_planks") to 200), sourceFile = "Nether"),
            ),
        )

        assertContains(html, "These 2 files are being imported as one project")
    }

    @Test
    fun `a single-file import still says schematic, not files`() {
        val html = render(
            requirements = mapOf(item("glass") to 4000, item("oak_planks") to 700, item("hopper") to 40),
            regions = listOf(frame, shell),
        )

        assertContains(html, "This schematic is built from 2 sections")
        assertFalse(html.contains("are being imported as one project"))
    }
}
