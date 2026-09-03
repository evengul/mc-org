package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.pipeline.project.ImportWarnings
import app.mcorg.pipeline.project.ResolvedRegion
import app.mcorg.pipeline.project.classifyImportWarnings
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
        containerCounts: Map<String, Int> = emptyMap(),
        warnings: ImportWarnings = ImportWarnings(),
    ) = importReviewPage(
        user = user,
        worldId = 1,
        worldName = "World",
        projectName = "Build",
        requirements = requirements,
        placedCounts = placedCounts,
        regions = regions,
        containerCounts = containerCounts,
        warnings = warnings,
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

    // --- Hard-capped rows (MCO-321) ---

    private val dragonEgg = Item("minecraft:dragon_egg", "Dragon Egg")

    @Test
    fun `a hard-capped row reaches the strip with its cap spelled out`() {
        // The world-eater case: 55 dragon eggs, one per TNT duper. The graph produces the item,
        // so nothing was said at all before — the plan simply told the user to break 55 of them.
        // A chip's hover text is not where "your world contains one of these" belongs.
        val html = render(
            requirements = mapOf(dragonEgg to 55, item("oak_planks") to 500),
            warnings = classifyImportWarnings(mapOf(dragonEgg to 55, item("oak_planks") to 500), null),
        )

        assertContains(html, "Hard limit in a world")
        assertContains(html, "Dragon Egg (55)")
        assertContains(html, "only from the first dragon")
        assertContains(html, "egg duplication")
        assertContains(html, "Limited supply", message = "and the row keeps its chip")
    }

    @Test
    fun `a hard-capped row is still included by default`() {
        // Unlike a creative-only row, this one is genuinely required — it is a duper component,
        // not decoration. Striking it would not give the user a build that works, so the warning
        // informs and nothing more.
        val html = render(
            requirements = mapOf(dragonEgg to 55),
            warnings = classifyImportWarnings(mapOf(dragonEgg to 55), null),
        )

        assertContains(html, "v1;1;minecraft:dragon_egg=55")
        assertFalse(html.contains("!minecraft:dragon_egg"), "an excluded row would carry the ! mark")
        assertContains(html, "checked=\"checked\"")
    }

    @Test
    fun `a capped row with nothing creative-only alongside it still gets a strip`() {
        // The reported import had zero blocked nodes, which is exactly why it went unnoticed:
        // the strip used to render only when there was something creative-only to say.
        val html = render(
            requirements = mapOf(dragonEgg to 50),
            warnings = classifyImportWarnings(mapOf(dragonEgg to 50), null),
        )

        assertContains(html, "callout__icon")
        assertFalse(html.contains("Not obtainable in survival"), "and says nothing it has no basis for")
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

    // --- stock vs structure (MCO-322) ---

    @Test
    fun `a fully stocked row is marked as container contents`() {
        val html = render(
            requirements = mapOf(item("redstone") to 3165, item("oak_planks") to 40),
            containerCounts = mapOf("minecraft:redstone" to 3165),
        )

        assertContains(html, "in containers")
        // Marked, not struck: the row is still checked and still in the list.
        assertContains(html, "minecraft:redstone=3165")
    }

    @Test
    fun `a partly stocked row says how much of it is stock`() {
        val html = render(
            requirements = mapOf(item("hopper") to 40),
            containerCounts = mapOf("minecraft:hopper" to 12),
        )

        assertContains(html, "12 in containers", message = "the count is what tells you the other 28 are structure")
    }

    @Test
    fun `a row with nothing in containers carries no marker`() {
        val html = render(requirements = mapOf(item("oak_planks") to 40))

        assertFalse(html.contains("in containers"))
        assertFalse(html.contains("import-review__stocked"))
    }

    @Test
    fun `a mostly stocked list says so above the rows`() {
        // The reported case in miniature: most of what looks like a build is what it is stocked
        // with. The chip is on one row of many; this is the part that gets read.
        val html = render(
            requirements = mapOf(item("redstone") to 3165, item("oak_planks") to 100),
            containerCounts = mapOf("minecraft:redstone" to 3165),
        )

        assertContains(html, "import-review__stocked-lead")
        assertContains(html, "96% of this list")
        assertContains(
            html,
            "3,165 units",
            message = "units, not items — the summary above already uses 'items' for distinct materials",
        )
        assertContains(html, "rather than blocks it places")
    }

    @Test
    fun `the lead reports a proportion rather than warning about one`() {
        // A stocked container is normal — a shulker loader full of redstone is what a shulker
        // loader is. The callout treatment stays reserved for creative-only rows (MCO-397).
        val html = render(
            requirements = mapOf(item("redstone") to 3165, item("oak_planks") to 100),
            containerCounts = mapOf("minecraft:redstone" to 3165),
        )

        assertContains(html, "That is normal for a farm or a sorter")
        assertFalse(html.contains("callout__icon"), "no warning icon for something that is not wrong")
    }

    @Test
    fun `a build with only a few filter items says nothing at all`() {
        // Every sorter has some. Announcing a 2% share would put this line on every import.
        val html = render(
            requirements = mapOf(item("oak_planks") to 1000, item("redstone") to 20),
            containerCounts = mapOf("minecraft:redstone" to 20),
        )

        assertFalse(html.contains("import-review__stocked-lead"), "below the threshold, the lead is silent")
        assertContains(html, "in containers", message = "but the row itself is still marked")
    }

}
