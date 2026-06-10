package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.SourceNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityOrderingTest {

    private fun item(name: String) = Item("minecraft:$name", name)

    private fun node(
        name: String,
        status: PlanNodeStatus,
        source: SourceNode? = null,
        supply: SupplySource? = null,
        vararg requires: Pair<String, Int>
    ) = PlanNode(
        item = item(name),
        quantity = 1, crafts = 1, leftover = 0,
        status = status,
        source = source,
        supply = supply,
        requires = requires.map { PlanRequirement(it.first, it.second) }
    )

    private fun source(type: ResourceSource.SourceType, filename: String) = SourceNode(type, filename)

    private val mine = source(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/x.json")
    private val kill = source(ResourceSource.SourceType.LootTypes.ENTITY, "entities/x.json")
    private val chestLoot = source(ResourceSource.SourceType.LootTypes.CHEST, "chests/x.json")
    private val barter = source(ResourceSource.SourceType.LootTypes.BARTER, "barter.json")
    private val smelt = source(ResourceSource.SourceType.RecipeTypes.SMELTING, "smelt.json")
    private val craft = source(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "craft.json")
    private val stonecut = source(ResourceSource.SourceType.RecipeTypes.STONECUTTING, "cut.json")

    @Test
    fun `independent leaves cluster by play-session group order`() {
        // All leaves — no topo constraints — so group order alone decides.
        val nodes = mapOf(
            "minecraft:cobblestone" to node("cobblestone", PlanNodeStatus.RAW_GATHER, mine),
            "minecraft:bone" to node("bone", PlanNodeStatus.RAW_GATHER, kill),
            "minecraft:saddle" to node("saddle", PlanNodeStatus.RAW_GATHER, chestLoot),
            "minecraft:ender_pearl" to node("ender_pearl", PlanNodeStatus.RAW_GATHER, barter),
            "minecraft:wool" to node("wool", PlanNodeStatus.SUPPLIED, supply = SupplySource.Farm("Sheep farm")),
            "minecraft:mystery" to node("mystery", PlanNodeStatus.BLOCKED)
        )
        val plan = GatheringPlan(nodes, listOf(PlanTarget(item("cobblestone"), 1)))

        val groups = plan.activityList.map { it.group }
        assertEquals(
            listOf(
                ActivityGroup.NEEDS_ATTENTION,
                ActivityGroup.COLLECT_SUPPLIED,
                ActivityGroup.GATHER,
                ActivityGroup.HUNT,
                ActivityGroup.LOOT,
                ActivityGroup.TRADE
            ),
            groups
        )
    }

    @Test
    fun `topological order dominates grouping`() {
        // Smelting consumes a crafted intermediate: CRAFT must still come first
        // even though SMELT precedes CRAFT in group order.
        val nodes = mapOf(
            "minecraft:glass" to node("glass", PlanNodeStatus.RESOLVED, smelt, null, "minecraft:sand_block" to 1),
            "minecraft:sand_block" to node("sand_block", PlanNodeStatus.RESOLVED, craft, null, "minecraft:sand" to 4),
            "minecraft:sand" to node("sand", PlanNodeStatus.RAW_GATHER, mine)
        )
        val plan = GatheringPlan(nodes, listOf(PlanTarget(item("glass"), 1)))

        val order = plan.activityList.map { it.item.id }
        assertTrue(order.indexOf("minecraft:sand") < order.indexOf("minecraft:sand_block"))
        assertTrue(order.indexOf("minecraft:sand_block") < order.indexOf("minecraft:glass"))
    }

    @Test
    fun `mining activities cluster together when topo allows`() {
        val nodes = mapOf(
            "minecraft:torch" to node(
                "torch", PlanNodeStatus.RESOLVED, craft, null,
                "minecraft:coal" to 1, "minecraft:stick" to 1
            ),
            "minecraft:coal" to node("coal", PlanNodeStatus.RAW_GATHER, mine),
            "minecraft:stick" to node("stick", PlanNodeStatus.RESOLVED, craft, null, "minecraft:bamboo" to 2),
            "minecraft:bamboo" to node("bamboo", PlanNodeStatus.RAW_GATHER, mine),
            "minecraft:string" to node("string", PlanNodeStatus.RAW_GATHER, kill)
        )
        val plan = GatheringPlan(nodes, listOf(PlanTarget(item("torch"), 1)))

        val order = plan.activityList.map { it.item.id }
        // Both mining leaves come before the hunt leaf, which comes before crafts.
        assertEquals(listOf("minecraft:bamboo", "minecraft:coal", "minecraft:string"), order.take(3))
        assertTrue(order.indexOf("minecraft:stick") < order.indexOf("minecraft:torch"))
    }

    @Test
    fun `stonecutting counts as bench work and barter as trade`() {
        val nodes = mapOf(
            "minecraft:stairs" to node("stairs", PlanNodeStatus.RESOLVED, stonecut, null, "minecraft:stone" to 1),
            "minecraft:stone" to node("stone", PlanNodeStatus.RAW_GATHER, mine),
            "minecraft:ender_pearl" to node("ender_pearl", PlanNodeStatus.RAW_GATHER, barter)
        )
        val plan = GatheringPlan(nodes, listOf(PlanTarget(item("stairs"), 1)))

        val byId = plan.activityList.associateBy { it.item.id }
        assertEquals(ActivityGroup.CRAFT, byId.getValue("minecraft:stairs").group)
        assertEquals(ActivityGroup.TRADE, byId.getValue("minecraft:ender_pearl").group)
        assertEquals(ActivityGroup.GATHER, byId.getValue("minecraft:stone").group)
    }

    @Test
    fun `needs-attention activities surface first and via the accessor`() {
        val tag = MinecraftTag("minecraft:planks", "Planks", emptyList())
        val nodes = mapOf(
            "minecraft:chest" to node(
                "chest", PlanNodeStatus.RESOLVED, craft, null,
                "minecraft:planks" to 8
            ),
            "minecraft:planks" to PlanNode(
                item = tag, quantity = 8, crafts = 8, leftover = 0, status = PlanNodeStatus.OPEN_TAG
            ),
            "minecraft:iron_ingot" to node("iron_ingot", PlanNodeStatus.RAW_GATHER, mine)
        )
        val plan = GatheringPlan(nodes, listOf(PlanTarget(item("chest"), 1)))

        assertEquals("minecraft:planks", plan.activityList.first().item.id)
        assertEquals(listOf("minecraft:planks"), plan.needsAttention.map { it.item.id })
    }

    @Test
    fun `ordering is stable across repeated derivations`() {
        val nodes = mapOf(
            "minecraft:a" to node("a", PlanNodeStatus.RAW_GATHER, mine),
            "minecraft:b" to node("b", PlanNodeStatus.RAW_GATHER, mine),
            "minecraft:c" to node("c", PlanNodeStatus.RAW_GATHER, kill),
            "minecraft:d" to node("d", PlanNodeStatus.RESOLVED, craft, null, "minecraft:a" to 1, "minecraft:c" to 1)
        )

        val first = GatheringPlan(nodes, listOf(PlanTarget(item("d"), 1))).activityList.map { it.item.id }
        repeat(5) {
            val again = GatheringPlan(nodes, listOf(PlanTarget(item("d"), 1))).activityList.map { it.item.id }
            assertEquals(first, again)
        }
        assertEquals(listOf("minecraft:a", "minecraft:b", "minecraft:c", "minecraft:d"), first)
    }
}
