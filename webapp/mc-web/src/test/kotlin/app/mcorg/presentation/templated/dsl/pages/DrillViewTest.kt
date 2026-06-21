package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.SourceNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.TargetTree
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [drillChainFragment].
 *
 * Verifies:
 * - The fragment wraps in #project-content for HTMX outerHTML swap
 * - Back-to-plan button targets the list lens
 * - Depth indentation via chain-node--depth-N classes
 * - Forced (1-way) nodes render a "· 1 way" label and no chip
 * - Multi-source nodes render a ⇄ chip (no-op placeholder)
 * - OPEN_TAG nodes render a ⇄ chip
 * - Fallback fragment renders gracefully with a callout
 */
class DrillViewTest {

    private val mine = SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/iron_ore.json")
    private val craft = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "iron_pickaxe.json")

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
}
