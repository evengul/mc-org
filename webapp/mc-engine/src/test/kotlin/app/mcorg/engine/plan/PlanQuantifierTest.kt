package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlanQuantifierTest {

    private val craft = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "recipe.json")
    private val mine = SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/some_block.json")

    private fun item(name: String) = Item("minecraft:$name", name)

    private fun resolved(name: String, produced: Int, vararg requires: Pair<String, Int>) = SelectedNode(
        item = item(name),
        status = PlanNodeStatus.RESOLVED,
        source = craft,
        producedQuantity = produced,
        requires = requires.map { PlanRequirement(it.first, it.second) }
    )

    private fun rawGather(name: String) = SelectedNode(
        item = item(name),
        status = PlanNodeStatus.RAW_GATHER,
        source = mine
    )

    /** chest <- 8 planks; planks (x4) <- 1 log; log raw. */
    private fun woodDag() = SelectedDag(
        nodes = linkedMapOf(
            "minecraft:chest" to resolved("chest", 1, "minecraft:oak_planks" to 8),
            "minecraft:stick" to resolved("stick", 4, "minecraft:oak_planks" to 2),
            "minecraft:oak_planks" to resolved("oak_planks", 4, "minecraft:oak_log" to 1),
            "minecraft:oak_log" to rawGather("oak_log")
        ),
        roots = mapOf("minecraft:chest" to "minecraft:chest", "minecraft:stick" to "minecraft:stick")
    )

    // ── single-target propagation ───────────────────────────────────────────

    @Test
    fun `single target propagates demand down the chain`() {
        val plan = PlanQuantifier.quantify(woodDag(), listOf(PlanTarget(item("chest"), 64)))

        val chest = plan.nodes.getValue("minecraft:chest")
        assertEquals(64, chest.quantity)
        assertEquals(64, chest.crafts)
        assertEquals(0, chest.leftover)

        val planks = plan.nodes.getValue("minecraft:oak_planks")
        assertEquals(512, planks.quantity)
        assertEquals(128, planks.crafts)
        assertEquals(0, planks.leftover)

        val log = plan.nodes.getValue("minecraft:oak_log")
        assertEquals(128, log.quantity)
        assertEquals(128, log.crafts)
    }

    @Test
    fun `ceil rounding reports the leftover per node`() {
        val plan = PlanQuantifier.quantify(woodDag(), listOf(PlanTarget(item("stick"), 10)))

        val stick = plan.nodes.getValue("minecraft:stick")
        assertEquals(10, stick.quantity)
        assertEquals(3, stick.crafts)      // ceil(10 / 4)
        assertEquals(2, stick.leftover)    // 3 * 4 - 10

        val planks = plan.nodes.getValue("minecraft:oak_planks")
        assertEquals(6, planks.quantity)   // 3 crafts * 2 planks
        assertEquals(2, planks.crafts)     // ceil(6 / 4)
        assertEquals(2, planks.leftover)

        assertEquals(2, plan.nodes.getValue("minecraft:oak_log").quantity)
    }

    // ── shared-node accumulation ────────────────────────────────────────────

    @Test
    fun `shared demand accumulates before ceiling - one craft instead of two`() {
        // Two consumers each need 2 planks; planks craft yields 4.
        // Accumulate-then-ceil: ceil((2+2)/4) = 1 craft. Per-target: 1+1 = 2.
        val dag = SelectedDag(
            nodes = linkedMapOf(
                "minecraft:bowl" to resolved("bowl", 1, "minecraft:oak_planks" to 2),
                "minecraft:pressure_plate" to resolved("pressure_plate", 1, "minecraft:oak_planks" to 2),
                "minecraft:oak_planks" to resolved("oak_planks", 4, "minecraft:oak_log" to 1),
                "minecraft:oak_log" to rawGather("oak_log")
            ),
            roots = mapOf(
                "minecraft:bowl" to "minecraft:bowl",
                "minecraft:pressure_plate" to "minecraft:pressure_plate"
            )
        )

        val plan = PlanQuantifier.quantify(
            dag,
            listOf(PlanTarget(item("bowl"), 1), PlanTarget(item("pressure_plate"), 1))
        )

        val planks = plan.nodes.getValue("minecraft:oak_planks")
        assertEquals(4, planks.quantity)
        assertEquals(1, planks.crafts)
        assertEquals(0, planks.leftover)
        assertEquals(1, plan.nodes.getValue("minecraft:oak_log").quantity)
    }

    @Test
    fun `two targets for the same item merge their demand`() {
        val plan = PlanQuantifier.quantify(
            woodDag(),
            listOf(PlanTarget(item("chest"), 32), PlanTarget(item("chest"), 32))
        )

        assertEquals(64, plan.nodes.getValue("minecraft:chest").quantity)
        assertEquals(512, plan.nodes.getValue("minecraft:oak_planks").quantity)
    }

    // ── terminals still quantified ──────────────────────────────────────────

    @Test
    fun `open tag and blocked nodes get quantities`() {
        val tag = MinecraftTag("minecraft:planks", "Planks", emptyList())
        val dag = SelectedDag(
            nodes = linkedMapOf(
                "minecraft:chest" to resolved("chest", 1, "minecraft:planks" to 8, "minecraft:hinge" to 1),
                "minecraft:planks" to SelectedNode(item = tag, status = PlanNodeStatus.OPEN_TAG),
                "minecraft:hinge" to SelectedNode(item = item("hinge"), status = PlanNodeStatus.BLOCKED)
            ),
            roots = mapOf("minecraft:chest" to "minecraft:chest")
        )

        val plan = PlanQuantifier.quantify(dag, listOf(PlanTarget(item("chest"), 2)))

        assertEquals(16, plan.nodes.getValue("minecraft:planks").quantity)
        assertEquals(2, plan.nodes.getValue("minecraft:hinge").quantity)
        assertTrue(plan.complete.not())
    }

    @Test
    fun `supplied nodes get quantities and keep their label`() {
        val farm = SupplySource.Farm("Plank farm")
        val dag = SelectedDag(
            nodes = linkedMapOf(
                "minecraft:chest" to resolved("chest", 1, "minecraft:oak_planks" to 8),
                "minecraft:oak_planks" to SelectedNode(
                    item = item("oak_planks"), status = PlanNodeStatus.SUPPLIED, supply = farm
                )
            ),
            roots = mapOf("minecraft:chest" to "minecraft:chest")
        )

        val plan = PlanQuantifier.quantify(dag, listOf(PlanTarget(item("chest"), 4)))

        val planks = plan.nodes.getValue("minecraft:oak_planks")
        assertEquals(32, planks.quantity)
        assertEquals(farm, planks.supply)
        assertTrue(plan.complete)
    }

    // ── surplus hook ────────────────────────────────────────────────────────

    @Test
    fun `a surplus policy reduces net demand before ceiling`() {
        val policy = SurplusPolicy { itemId, _ -> if (itemId == "minecraft:stick") 2L else 0L }

        val plan = PlanQuantifier.quantify(woodDag(), listOf(PlanTarget(item("stick"), 10)), policy)

        val stick = plan.nodes.getValue("minecraft:stick")
        assertEquals(10, stick.quantity)   // reported demand stays the real demand
        assertEquals(2, stick.crafts)      // ceil((10 - 2) / 4)
        assertEquals(0, stick.leftover)
        assertEquals(4, plan.nodes.getValue("minecraft:oak_planks").quantity)
    }

    @Test
    fun `the default policy draws nothing`() {
        val plan = PlanQuantifier.quantify(woodDag(), listOf(PlanTarget(item("stick"), 10)))

        assertEquals(3, plan.nodes.getValue("minecraft:stick").crafts)
    }

    // ── robustness ──────────────────────────────────────────────────────────

    @Test
    fun `targets unknown to the dag are ignored`() {
        val plan = PlanQuantifier.quantify(
            woodDag(),
            listOf(PlanTarget(item("chest"), 1), PlanTarget(item("unobtainium"), 5))
        )

        assertEquals(1, plan.nodes.getValue("minecraft:chest").quantity)
        assertTrue("minecraft:unobtainium" !in plan.nodes)
    }

    @Test
    fun `non-positive target amounts are dropped`() {
        val plan = PlanQuantifier.quantify(
            woodDag(),
            listOf(PlanTarget(item("chest"), 0), PlanTarget(item("stick"), 4))
        )

        assertEquals(0, plan.nodes.getValue("minecraft:chest").quantity)
        assertEquals(listOf("minecraft:stick"), plan.targets.map { it.item.id })
    }

    // ── end to end through the facade ───────────────────────────────────────

    @Test
    fun `select plus quantify produce the full plan for a real graph`() {
        val log = item("oak_log")
        val planks = item("oak_planks")
        val chest = item("chest")
        val stick = item("stick")

        val builder = ItemSourceGraph.builder()
        fun add(type: ResourceSource.SourceType, filename: String, output: Pair<Item, Int>, vararg inputs: Pair<Item, Int>) {
            val source = builder.addSourceNode(type, filename)
            builder.addSourceToItemEdge(source, builder.addItemNode(output.first), output.second)
            for ((input, qty) in inputs) builder.addItemToSourceEdge(builder.addItemNode(input), source, qty)
        }
        add(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/oak_log.json", log to 1)
        add(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "oak_planks.json", planks to 4, log to 1)
        add(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "chest.json", chest to 1, planks to 8)
        add(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "stick.json", stick to 4, planks to 2)
        val graph = builder.build()

        val plan = GatheringPlanner.plan(
            graph,
            listOf(PlanTarget(chest, 64), PlanTarget(stick, 64))
        )

        assertTrue(plan.complete)
        assertEquals(544, plan.nodes.getValue("minecraft:oak_planks").quantity) // 512 + 32
        assertEquals(136, plan.nodes.getValue("minecraft:oak_log").quantity)

        val order = plan.activityList.map { it.item.id }
        assertTrue(order.indexOf("minecraft:oak_log") < order.indexOf("minecraft:oak_planks"))
        assertTrue(order.indexOf("minecraft:oak_planks") < order.indexOf("minecraft:chest"))

        val drillDown = plan.perTarget("minecraft:stick")
        assertNotNull(drillDown)
        assertEquals(32, drillDown.children.single().quantityIfAlone)
    }
}
