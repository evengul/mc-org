package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.TargetTree
import app.mcorg.domain.model.project.Project
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [drillChainFragment], [drillNotFoundFragment], and [nodePickerFragment].
 *
 * Verifies:
 * - The fragment wraps in #project-content for HTMX outerHTML swap
 * - Back-to-plan button targets the list lens
 * - Depth indentation via chain-node--depth-N classes
 * - Forced (1-way) nodes render a "· 1 way" label and no chip
 * - Multi-source nodes render a ⇄ chip wired to the sources endpoint
 * - OPEN_TAG nodes render a ⇄ chip
 * - The chip links to the /sources?node= endpoint
 * - A picker slot div is emitted for chip nodes
 * - nodePickerFragment: source picker renders options with hx-post to /pin
 * - nodePickerFragment: tag picker renders members with hx-post to /tag
 * - nodePickerFragment: selected option has picker-opt--sel
 * - nodePickerFragment: >30 cap shows "Showing 30 of N" note
 * - nodePickerFragment: clear control appears when override is active
 * - Fallback fragment renders gracefully with a callout
 */
class DrillViewTest {

    private val mine = SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/iron_ore.json")
    private val craft = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "iron_pickaxe.json")
    private val smelt = SourceNode(ResourceSource.SourceType.RecipeTypes.SMELTING, "smelting/iron_ingot.json")

    private fun item(id: String, name: String) = Item("minecraft:$id", name)

    private fun testProject(worldId: Int = 1, projectId: Int = 2) = Project(
        id = projectId,
        worldId = worldId,
        name = "Test Project",
        description = "",
        type = ProjectType.BUILDING,
        stage = ProjectStage.PLANNING,
        state = ProjectState.ACTIVE,
        location = null,
        tasksTotal = 0,
        tasksCompleted = 0,
        importedFromIdea = null,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now()
    )

    /** Build a tiny ItemSourceGraph with two sources for iron ingot. */
    private fun ironGraph(): ItemSourceGraph {
        val ironIngot = item("iron_ingot", "Iron Ingot")
        val ironOre = item("iron_ore", "Iron Ore")
        val builder = ItemSourceGraph.builder()

        val mineSource = builder.addSourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/iron_ore.json")
        builder.addSourceToItemEdge(mineSource, builder.addItemNode(ironIngot))
        builder.addSourceToItemEdge(mineSource, builder.addItemNode(ironOre))

        val smeltSource = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.SMELTING, "smelting/iron_ingot.json")
        builder.addSourceToItemEdge(smeltSource, builder.addItemNode(ironIngot))
        builder.addItemToSourceEdge(builder.addItemNode(ironOre), smeltSource)

        return builder.build()
    }

    // -------------------------------------------------------------------------
    // Fragment structure
    // -------------------------------------------------------------------------

    @Test
    fun `fragment wraps in project-content div for outerHTML swap`() {
        val tree = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 64,
            craftsIfAlone = 64,
            status = PlanNodeStatus.RAW_GATHER,
            source = mine,
        )
        val html = drillChainFragment(testProject(), tree, emptyMap())

        assertTrue(html.contains("id=\"project-content\""),
            "Expected id='project-content' in: $html")
    }

    @Test
    fun `fragment contains back-to-plan button linking to list lens fragment`() {
        val tree = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 64,
            craftsIfAlone = 64,
            status = PlanNodeStatus.RAW_GATHER,
            source = mine,
        )
        val html = drillChainFragment(testProject(worldId = 3, projectId = 7), tree, emptyMap())

        assertContains(html, "detail-content?lens=list")
        assertContains(html, "Back to plan")
    }

    @Test
    fun `target item name appears in section label`() {
        val tree = TargetTree(
            item = item("iron_pickaxe", "Iron Pickaxe"),
            quantityIfAlone = 2,
            craftsIfAlone = 2,
            status = PlanNodeStatus.RESOLVED,
            source = craft,
        )
        val html = drillChainFragment(testProject(), tree, emptyMap())

        assertContains(html, "Iron Pickaxe")
        assertContains(html, "chain")
    }

    // -------------------------------------------------------------------------
    // Depth indentation
    // -------------------------------------------------------------------------

    @Test
    fun `root node has depth-0 class and child has depth-1 class`() {
        val child = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RAW_GATHER,
            source = mine,
        )
        val root = TargetTree(
            item = item("iron_pickaxe", "Iron Pickaxe"),
            quantityIfAlone = 2,
            craftsIfAlone = 2,
            status = PlanNodeStatus.RESOLVED,
            source = craft,
            children = listOf(child),
        )
        val html = drillChainFragment(testProject(), root, emptyMap())

        assertContains(html, "chain-node--depth-0")
        assertContains(html, "chain-node--depth-1")
    }

    @Test
    fun `nodes deeper than 4 cap at depth-4`() {
        fun deepTree(depth: Int): TargetTree {
            return if (depth == 0) {
                TargetTree(
                    item = item("leaf", "Leaf"),
                    quantityIfAlone = 1,
                    craftsIfAlone = 1,
                    status = PlanNodeStatus.RAW_GATHER,
                    source = mine,
                )
            } else {
                TargetTree(
                    item = item("node_$depth", "Node $depth"),
                    quantityIfAlone = 1L,
                    craftsIfAlone = 1L,
                    status = PlanNodeStatus.RESOLVED,
                    source = craft,
                    children = listOf(deepTree(depth - 1)),
                )
            }
        }

        val root = deepTree(6) // 7 levels deep
        val html = drillChainFragment(testProject(), root, emptyMap())

        assertContains(html, "chain-node--depth-4") // capped at 4
        assertFalse(html.contains("chain-node--depth-5"), "Depth should cap at 4 but got depth-5 in: $html")
    }

    // -------------------------------------------------------------------------
    // 1-way (forced) nodes
    // -------------------------------------------------------------------------

    @Test
    fun `forced node (1 candidate) renders 1 way label and no chip`() {
        val tree = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 64,
            craftsIfAlone = 64,
            status = PlanNodeStatus.RAW_GATHER,
            source = mine,
        )
        // 1 candidate → forced
        val candidateCounts = mapOf("minecraft:iron_ingot" to 1)
        val html = drillChainFragment(testProject(), tree, candidateCounts)

        assertContains(html, "1 way")
        assertFalse(html.contains("class=\"chip\""), "Should not render chip for forced node, but got: $html")
    }

    @Test
    fun `node with 0 candidates renders 1 way label`() {
        val tree = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 64,
            craftsIfAlone = 64,
            status = PlanNodeStatus.RAW_GATHER,
            source = mine,
        )
        val candidateCounts = mapOf("minecraft:iron_ingot" to 0)
        val html = drillChainFragment(testProject(), tree, candidateCounts)

        assertContains(html, "1 way")
    }

    // -------------------------------------------------------------------------
    // Multi-source nodes (⇄ chip)
    // -------------------------------------------------------------------------

    @Test
    fun `multi-source node (2+ candidates) renders chip with source name`() {
        val tree = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        // 3 candidates → multi-source chip
        val candidateCounts = mapOf("minecraft:iron_ingot" to 3)
        val html = drillChainFragment(testProject(), tree, candidateCounts)

        assertContains(html, "class=\"chip\"")
        assertContains(html, "⇄")
        assertFalse(html.contains("1 way"), "Should not show '1 way' for multi-source node, got: $html")
    }

    @Test
    fun `multi-source node chip contains current source method name`() {
        val tree = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine, // mine = BLOCK → getName() = "Break Block: Iron ore"
        )
        val candidateCounts = mapOf("minecraft:iron_ingot" to 2)
        val html = drillChainFragment(testProject(), tree, candidateCounts)

        assertContains(html, "⇄")
        // The chip label comes from getName() which includes "Break Block"
        assertContains(html, "Break Block")
    }

    @Test
    fun `multi-source chip has hx-get wired to sources endpoint`() {
        val tree = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        val candidateCounts = mapOf("minecraft:iron_ingot" to 2)
        val html = drillChainFragment(testProject(worldId = 5, projectId = 9), tree, candidateCounts)

        // Chip must hx-get the /sources endpoint
        assertContains(html, "/sources?node=")
        assertContains(html, "hx-get")
    }

    @Test
    fun `multi-source chip has a picker slot div with correct id`() {
        val tree = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        val candidateCounts = mapOf("minecraft:iron_ingot" to 2)
        val html = drillChainFragment(testProject(), tree, candidateCounts)

        // Picker slot id = "picker-{slug}" where slug = id with every non-alphanumeric → '-'
        // (so "minecraft:iron_ingot" → "minecraft-iron-ingot"; also neutralises '#' in tag ids).
        assertContains(html, "picker-minecraft-iron-ingot")
    }

    @Test
    fun `drillChainContent renders the chain body without the project-content wrapper`() {
        // This is the body shared with the full page shell for ?drill= deep-links.
        val tree = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        val html = createHTML().div {
            drillChainContent(testProject(worldId = 3, projectId = 7), tree, mapOf("minecraft:iron_ingot" to 1))
        }

        assertContains(html, "drill-chain")
        assertContains(html, "Back to plan")
        assertContains(html, "Iron Ingot")
        // Back-to-plan returns to the list lens of this project.
        assertContains(html, "/worlds/3/projects/7?lens=list")
    }

    // -------------------------------------------------------------------------
    // OPEN_TAG nodes (⇄ chip — pick variant)
    // -------------------------------------------------------------------------

    @Test
    fun `OPEN_TAG node renders chip with pick variant label`() {
        val tree = TargetTree(
            item = item("planks", "Any Planks"),
            quantityIfAlone = 320,
            craftsIfAlone = 320,
            status = PlanNodeStatus.OPEN_TAG,
            source = null,
        )
        val html = drillChainFragment(testProject(), tree, emptyMap())

        assertContains(html, "class=\"chip\"")
        assertContains(html, "⇄")
        assertContains(html, "Pick variant")
    }

    @Test
    fun `OPEN_TAG chip has hx-get wired to sources endpoint`() {
        val tree = TargetTree(
            item = item("planks", "Any Planks"),
            quantityIfAlone = 320,
            craftsIfAlone = 320,
            status = PlanNodeStatus.OPEN_TAG,
            source = null,
        )
        val html = drillChainFragment(testProject(worldId = 3, projectId = 8), tree, emptyMap())

        assertContains(html, "hx-get")
        assertContains(html, "/sources?node=")
    }

    // -------------------------------------------------------------------------
    // SUPPLIED / BLOCKED nodes
    // -------------------------------------------------------------------------

    @Test
    fun `SUPPLIED node renders supply label with no chip`() {
        val tree = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 64,
            craftsIfAlone = 64,
            status = PlanNodeStatus.SUPPLIED,
        )
        val html = drillChainFragment(testProject(), tree, emptyMap())

        assertContains(html, "Supplied")
        assertFalse(html.contains("class=\"chip\""), "SUPPLIED node should not have a chip, got: $html")
    }

    @Test
    fun `BLOCKED node renders blocked label with no chip`() {
        val tree = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 64,
            craftsIfAlone = 64,
            status = PlanNodeStatus.BLOCKED,
        )
        val html = drillChainFragment(testProject(), tree, emptyMap())

        assertContains(html, "Blocked")
        assertFalse(html.contains("class=\"chip\""), "BLOCKED node should not have a chip, got: $html")
    }

    // -------------------------------------------------------------------------
    // Quantity
    // -------------------------------------------------------------------------

    @Test
    fun `quantityIfAlone appears in node row`() {
        val tree = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 1728,
            craftsIfAlone = 1728,
            status = PlanNodeStatus.RAW_GATHER,
            source = mine,
        )
        val html = drillChainFragment(testProject(), tree, emptyMap())

        assertContains(html, "1728")
    }

    // -------------------------------------------------------------------------
    // Fallback fragment
    // -------------------------------------------------------------------------

    @Test
    fun `drillNotFoundFragment renders project-content with callout and back button`() {
        val html = drillNotFoundFragment(testProject(worldId = 5, projectId = 10), "Item not found in plan.")

        assertTrue(html.contains("id=\"project-content\""),
            "Expected id='project-content' in fallback: $html")
        assertContains(html, "Back to plan")
        assertContains(html, "callout")
        assertContains(html, "Item not found in plan.")
    }

    // -------------------------------------------------------------------------
    // nodePickerFragment — source picker
    // -------------------------------------------------------------------------

    @Test
    fun `source picker renders options with hx-post to pin endpoint`() {
        val ironIngotItem = item("iron_ingot", "Iron Ingot")
        val node = TargetTree(
            item = ironIngotItem,
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        val graph = ironGraph()

        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:iron_ingot",
            node = node,
            graph = graph,
            activeSourceKey = null,
            activeMemberId = null,
            demand = node.quantityIfAlone,
        )

        // "Choose source" label
        assertContains(html, "Choose source")
        // hx-post to /pin endpoint
        assertContains(html, "hx-post")
        assertContains(html, "/pin")
        // contains source names from graph
        assertContains(html, "Break Block")
    }

    @Test
    fun `source picker marks active source as selected`() {
        val ironIngotItem = item("iron_ingot", "Iron Ingot")
        val node = TargetTree(
            item = ironIngotItem,
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        val graph = ironGraph()
        val activeKey = mine.getKey() // "minecraft:block:blocks/iron_ore.json"

        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:iron_ingot",
            node = node,
            graph = graph,
            activeSourceKey = activeKey,
            activeMemberId = null,
            demand = node.quantityIfAlone,
        )

        // Selected option has the --sel modifier class
        assertContains(html, "picker-opt--sel")
    }

    @Test
    fun `source picker marks the top-ranked candidate with best score star`() {
        val node = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:iron_ingot",
            node = node,
            graph = ironGraph(),
            activeSourceKey = null,
            activeMemberId = null,
            demand = node.quantityIfAlone,
        )

        // Exactly the rank-0 candidate carries the marker.
        assertContains(html, "best score ★")
    }

    @Test
    fun `source picker labels recipe sources by their ingredients`() {
        // smeltSource consumes iron_ore (an ItemToSource edge in ironGraph), so its option
        // is named "from Iron Ore" rather than the indistinguishable bare "Smelting".
        val node = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:iron_ingot",
            node = node,
            graph = ironGraph(),
            activeSourceKey = null,
            activeMemberId = null,
            demand = node.quantityIfAlone,
        )

        assertContains(html, "from Iron Ore")
    }

    @Test
    fun `picker threads origin=list into option posts and clear url`() {
        val node = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:iron_ingot",
            node = node,
            graph = ironGraph(),
            activeSourceKey = mine.getKey(), // forces the clear control to render
            activeMemberId = null,
            demand = node.quantityIfAlone,
            origin = "list",
        )

        // hx-vals JSON quotes are HTML-escaped to &quot; in the rendered attribute.
        assertContains(html, "origin&quot;:&quot;list&quot;") // carried in the pin hx-vals
        assertContains(html, "origin=list")                  // carried on the clear url
    }

    @Test
    fun `picker omits origin when not provided`() {
        val node = TargetTree(
            item = item("iron_ingot", "Iron Ingot"),
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:iron_ingot",
            node = node,
            graph = ironGraph(),
            activeSourceKey = null,
            activeMemberId = null,
            demand = node.quantityIfAlone,
        )

        assertFalse(html.contains("origin"), "origin should be absent for drill-origin pickers")
    }

    @Test
    fun `source picker shows clear button when override is active`() {
        val ironIngotItem = item("iron_ingot", "Iron Ingot")
        val node = TargetTree(
            item = ironIngotItem,
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        val graph = ironGraph()

        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:iron_ingot",
            node = node,
            graph = graph,
            activeSourceKey = mine.getKey(),
            activeMemberId = null,
            demand = node.quantityIfAlone,
        )

        assertContains(html, "Clear override")
        assertContains(html, "/override")
        assertContains(html, "hx-delete")
    }

    @Test
    fun `source picker does not show clear button when no override is active`() {
        val ironIngotItem = item("iron_ingot", "Iron Ingot")
        val node = TargetTree(
            item = ironIngotItem,
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        val graph = ironGraph()

        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:iron_ingot",
            node = node,
            graph = graph,
            activeSourceKey = null,
            activeMemberId = null,
            demand = node.quantityIfAlone,
        )

        assertFalse(html.contains("Clear override"), "Clear button should not appear when no override active")
    }

    @Test
    fun `source picker with empty graph shows no-sources message`() {
        val ironIngotItem = item("iron_ingot", "Iron Ingot")
        val node = TargetTree(
            item = ironIngotItem,
            quantityIfAlone = 200,
            craftsIfAlone = 200,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )
        // No graph (null) → no candidates
        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:iron_ingot",
            node = node,
            graph = null,
            activeSourceKey = null,
            activeMemberId = null,
            demand = node.quantityIfAlone,
        )

        assertContains(html, "No sources available")
    }

    @Test
    fun `source picker with more than 30 candidates shows truncation note`() {
        // Build a graph with 35 sources for one item
        val ironIngotItem = item("iron_ingot", "Iron Ingot")
        val builder = ItemSourceGraph.builder()
        val itemNode = builder.addItemNode(ironIngotItem)
        for (i in 1..35) {
            val src = builder.addSourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/ore_$i.json")
            builder.addSourceToItemEdge(src, itemNode)
        }
        val graph = builder.build()

        val node = TargetTree(
            item = ironIngotItem,
            quantityIfAlone = 100,
            craftsIfAlone = 100,
            status = PlanNodeStatus.RESOLVED,
            source = mine,
        )

        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:iron_ingot",
            node = node,
            graph = graph,
            activeSourceKey = null,
            activeMemberId = null,
            demand = node.quantityIfAlone,
        )

        // Truncation note present, with a search box for high fan-out
        assertContains(html, "Showing 30 of 35")
        assertContains(html, "search to narrow")
        assertContains(html, "picker-search")
    }

    // -------------------------------------------------------------------------
    // nodePickerFragment — tag member picker
    // -------------------------------------------------------------------------

    @Test
    fun `tag picker renders member options with hx-post to tag endpoint`() {
        val oakPlanks = Item("minecraft:oak_planks", "Oak Planks")
        val sprucePlanks = Item("minecraft:spruce_planks", "Spruce Planks")
        val tag = MinecraftTag("#minecraft:planks", "Any Planks", listOf(oakPlanks, sprucePlanks))

        val node = TargetTree(
            item = tag,
            quantityIfAlone = 320,
            craftsIfAlone = 320,
            status = PlanNodeStatus.OPEN_TAG,
            source = null,
        )

        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:chest",
            node = node,
            graph = null,
            activeSourceKey = null,
            activeMemberId = null,
            demand = node.quantityIfAlone,
        )

        assertContains(html, "Pick a variant")
        assertContains(html, "hx-post")
        assertContains(html, "/tag")
        assertContains(html, "Oak Planks")
        assertContains(html, "Spruce Planks")
    }

    @Test
    fun `tag picker marks active member as selected`() {
        val oakPlanks = Item("minecraft:oak_planks", "Oak Planks")
        val sprucePlanks = Item("minecraft:spruce_planks", "Spruce Planks")
        val tag = MinecraftTag("#minecraft:planks", "Any Planks", listOf(oakPlanks, sprucePlanks))

        val node = TargetTree(
            item = tag,
            quantityIfAlone = 320,
            craftsIfAlone = 320,
            status = PlanNodeStatus.OPEN_TAG,
            source = null,
        )

        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:chest",
            node = node,
            graph = null,
            activeSourceKey = null,
            activeMemberId = "minecraft:oak_planks",
            demand = node.quantityIfAlone,
        )

        assertContains(html, "picker-opt--sel")
        assertContains(html, "selected")
    }

    @Test
    fun `tag picker with more than 30 members shows truncation note`() {
        val members = (1..35).map { Item("minecraft:planks_$it", "Planks $it") }
        val tag = MinecraftTag("#minecraft:planks", "Any Planks", members)

        val node = TargetTree(
            item = tag,
            quantityIfAlone = 320,
            craftsIfAlone = 320,
            status = PlanNodeStatus.OPEN_TAG,
            source = null,
        )

        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:chest",
            node = node,
            graph = null,
            activeSourceKey = null,
            activeMemberId = null,
            demand = node.quantityIfAlone,
        )

        assertContains(html, "Showing 30 of 35")
    }

    @Test
    fun `tag picker shows clear button when member override is active`() {
        val oakPlanks = Item("minecraft:oak_planks", "Oak Planks")
        val tag = MinecraftTag("#minecraft:planks", "Any Planks", listOf(oakPlanks))

        val node = TargetTree(
            item = tag,
            quantityIfAlone = 320,
            craftsIfAlone = 320,
            status = PlanNodeStatus.OPEN_TAG,
            source = null,
        )

        val html = nodePickerFragment(
            worldId = 1,
            projectId = 2,
            targetItemId = "minecraft:chest",
            node = node,
            graph = null,
            activeSourceKey = null,
            activeMemberId = "minecraft:oak_planks",
            demand = node.quantityIfAlone,
        )

        assertContains(html, "Clear override")
        assertContains(html, "hx-delete")
        assertContains(html, "/override")
    }
}
