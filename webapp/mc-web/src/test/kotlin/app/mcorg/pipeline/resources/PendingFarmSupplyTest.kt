package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanTarget
import app.mcorg.engine.plan.SupplySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingFarmSupplyTest {

    private val ironIngot = Item("minecraft:iron_ingot", "Iron Ingot")
    private val goldIngot = Item("minecraft:gold_ingot", "Gold Ingot")
    private val bamboo = Item("minecraft:bamboo", "Bamboo")

    private fun plan(vararg nodes: PlanNode) = GatheringPlan(
        nodes = nodes.associateBy { it.item.id },
        targets = nodes.map { PlanTarget(it.item, it.quantity) },
    )

    private fun node(
        item: Item,
        quantity: Long,
        status: PlanNodeStatus = PlanNodeStatus.RAW_GATHER,
        supply: SupplySource? = null,
    ) = PlanNode(item = item, quantity = quantity, crafts = 0, leftover = 0, status = status, supply = supply)

    private fun row(itemId: String, projectId: Int = 7, projectName: String = "Iron Farm") =
        PlannedFarmRow(itemId = itemId, projectId = projectId, projectName = projectName)

    @Test
    fun `an item still gathered by hand that a planned farm produces becomes a pending supply`() {
        val result = buildPendingFarmSupplies(
            plan(node(ironIngot, 32)),
            listOf(row(ironIngot.id)),
        )

        assertEquals(1, result.size)
        val farm = result.first()
        assertEquals("Iron Farm", farm.projectName)
        assertEquals(7, farm.projectId)
        assertEquals(listOf(PendingFarmItem("minecraft:iron_ingot", "Iron Ingot", 32)), farm.items)
    }

    @Test
    fun `an already supplied item is not pending`() {
        // An operational farm (or a linked project) already solves it — there is nothing
        // to promise, and the row lives in "Collect from farms" instead.
        val result = buildPendingFarmSupplies(
            plan(node(ironIngot, 32, PlanNodeStatus.SUPPLIED, SupplySource.Farm("Other Iron Farm"))),
            listOf(row(ironIngot.id)),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a planned farm producing nothing this plan needs is not mentioned`() {
        val result = buildPendingFarmSupplies(
            plan(node(ironIngot, 32)),
            listOf(row(bamboo.id, projectId = 9, projectName = "Bamboo Farm")),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `items are grouped per farm, biggest amount first`() {
        val result = buildPendingFarmSupplies(
            plan(node(ironIngot, 32), node(goldIngot, 96), node(bamboo, 12)),
            listOf(
                row(ironIngot.id),
                row(goldIngot.id),
                row(bamboo.id, projectId = 9, projectName = "Bamboo Farm"),
            ),
        )

        assertEquals(listOf("Bamboo Farm", "Iron Farm"), result.map { it.projectName }, "farms sort by name")
        val iron = result.first { it.projectName == "Iron Farm" }
        assertEquals(listOf("Gold Ingot", "Iron Ingot"), iron.items.map { it.itemName })
        assertEquals(listOf(96L, 32L), iron.items.map { it.quantity })
    }

    @Test
    fun `no planned farms means no notice`() {
        assertTrue(buildPendingFarmSupplies(plan(node(ironIngot, 32)), emptyList()).isEmpty())
    }
}
