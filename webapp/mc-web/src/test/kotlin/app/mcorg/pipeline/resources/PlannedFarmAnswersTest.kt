package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanRequirement
import app.mcorg.engine.plan.PlanTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-542 — which roll-up lines a farm project in this world already answers.
 *
 * The cases are the YAMS world's (Roadmap Testing World, project 44): a farm that makes a
 * roll-up line outright, and the iron farm that makes the ingot the roll-up's ore is mined for.
 */
class PlannedFarmAnswersTest {

    private val ironIngot = Item("minecraft:iron_ingot", "Iron Ingot")
    private val deepslateIronOre = Item("minecraft:deepslate_iron_ore", "Deepslate Iron Ore")
    private val ironBlock = Item("minecraft:iron_block", "Block of Iron")
    private val furnace = Item("minecraft:blast_furnace", "Blast Furnace")
    private val cobblestone = Item("minecraft:cobblestone", "Cobblestone")
    private val redstone = Item("minecraft:redstone", "Redstone Dust")
    private val ice = Item("minecraft:ice", "Ice")

    private fun plan(vararg nodes: PlanNode) = GatheringPlan(
        nodes = nodes.associateBy { it.item.id },
        targets = listOf(PlanTarget(nodes.first().item, nodes.first().quantity)),
    )

    private fun node(
        item: MinecraftId,
        quantity: Long,
        status: PlanNodeStatus = PlanNodeStatus.RAW_GATHER,
        requires: List<PlanRequirement> = emptyList(),
    ) = PlanNode(item = item, quantity = quantity, crafts = 0, leftover = 0, status = status, requires = requires)

    private fun demand(item: Item, quantity: Long) = FarmScaleDemand(item.id, item.name, quantity)

    private fun farm(id: Int, name: String, vararg items: Pair<Item, Long>) = PendingFarmSupply(
        projectId = id,
        projectName = name,
        items = items.map { (item, quantity) -> PendingFarmItem(item.id, item.name, quantity) },
    )

    @Test
    fun `a line a farm makes is answered by that farm`() {
        val plan = plan(node(cobblestone, 51_437), node(ice, 2_281))

        val answers = PlannedFarmAnswers.of(
            plan,
            listOf(demand(cobblestone, 51_437), demand(ice, 2_281)),
            listOf(farm(7, "231k-924k Cobblestone farm", cobblestone to 51_437)),
        )

        val answer = answers.single()
        assertEquals(7, answer.projectId)
        assertEquals(listOf(cobblestone.id), answer.makes.map { it.itemId })
        assertTrue(answer.alsoRemoves.isEmpty())
    }

    @Test
    fun `ore mined only to make the ingot a farm makes is a knock-on, naming the ingot`() {
        val plan = plan(
            node(ironIngot, 32_949, PlanNodeStatus.RESOLVED, listOf(PlanRequirement(deepslateIronOre.id, 1))),
            node(deepslateIronOre, 32_949),
        )

        val answer = PlannedFarmAnswers.of(
            plan,
            listOf(demand(deepslateIronOre, 32_949)),
            listOf(farm(9, "3.8k 8 Pod Iron Farm", ironIngot to 32_949)),
        ).single()

        // The farm does not make ore. Iron Ingot is not a roll-up line, so nothing is "made" here.
        assertTrue(answer.makes.isEmpty())
        val knockOn = answer.alsoRemoves.single()
        assertEquals(deepslateIronOre.id, knockOn.demand.itemId)
        assertEquals(listOf("Iron Ingot"), knockOn.feeds)
    }

    @Test
    fun `the knock-on names the farm's item even through an intermediate step`() {
        // Ore -> ingot -> block, and the farm makes the block: the ore still only exists to feed it.
        val plan = plan(
            node(ironBlock, 2_000, PlanNodeStatus.RESOLVED, listOf(PlanRequirement(ironIngot.id, 9))),
            node(ironIngot, 18_000, PlanNodeStatus.RESOLVED, listOf(PlanRequirement(deepslateIronOre.id, 1))),
            node(deepslateIronOre, 18_000),
        )

        val answer = PlannedFarmAnswers.of(
            plan,
            listOf(demand(deepslateIronOre, 18_000)),
            listOf(farm(9, "Iron Block Farm", ironBlock to 2_000)),
        ).single()

        assertEquals(listOf("Block of Iron"), answer.alsoRemoves.single().feeds)
    }

    @Test
    fun `ore that also feeds something the farm does not cover is not claimed`() {
        // Same conservative rule as a design's knock-on: the blast furnace still needs the ore
        // smelted into ingots, so building the iron farm does not make the mining go away.
        val plan = plan(
            node(furnace, 100, PlanNodeStatus.RESOLVED, listOf(PlanRequirement(ironIngot.id, 5))),
            node(ironBlock, 2_000, PlanNodeStatus.RESOLVED, listOf(PlanRequirement(ironIngot.id, 9))),
            node(ironIngot, 18_500, PlanNodeStatus.RESOLVED, listOf(PlanRequirement(deepslateIronOre.id, 1))),
            node(deepslateIronOre, 18_500),
        )

        val answers = PlannedFarmAnswers.of(
            plan,
            listOf(demand(deepslateIronOre, 18_500)),
            listOf(farm(9, "Iron Block Farm", ironBlock to 2_000)),
        )

        assertTrue(answers.isEmpty(), "nothing on the roll-up is answered: $answers")
    }

    @Test
    fun `a farm whose items are not on the roll-up answers nothing`() {
        // 32 iron ingots is a real prerequisite, and the Prerequisites section says so, but there
        // is no farm-scale line for it to annotate.
        val plan = plan(node(ironIngot, 32), node(ice, 2_281))

        val answers = PlannedFarmAnswers.of(
            plan,
            listOf(demand(ice, 2_281)),
            listOf(farm(9, "Iron Farm", ironIngot to 32)),
        )

        assertTrue(answers.isEmpty())
    }

    @Test
    fun `a line two farms make is claimed once`() {
        val plan = plan(node(redstone, 63_213))

        val answers = PlannedFarmAnswers.of(
            plan,
            listOf(demand(redstone, 63_213)),
            listOf(farm(3, "A Witch Farm", redstone to 63_213), farm(4, "B Witch Farm", redstone to 63_213)),
        )

        assertEquals(listOf(3), answers.map { it.projectId }, "the roll-up lists each line once")
    }

    @Test
    fun `a farm that makes a line outright beats another farm's knock-on for it`() {
        val plan = plan(
            node(ironIngot, 32_949, PlanNodeStatus.RESOLVED, listOf(PlanRequirement(deepslateIronOre.id, 1))),
            node(deepslateIronOre, 32_949),
        )

        val answers = PlannedFarmAnswers.of(
            plan,
            listOf(demand(deepslateIronOre, 32_949)),
            // Sorted by name, as prerequisiteFarmsFor returns them, so the knock-on farm comes first.
            listOf(farm(1, "A Iron Farm", ironIngot to 32_949), farm(2, "B Ore Farm", deepslateIronOre to 32_949)),
        )

        assertEquals(listOf(2), answers.map { it.projectId })
        assertEquals(listOf(deepslateIronOre.id), answers.single().makes.map { it.itemId })
    }

    @Test
    fun `answeredIds covers both what is made and what is knocked on`() {
        val plan = plan(
            node(ironIngot, 32_949, PlanNodeStatus.RESOLVED, listOf(PlanRequirement(deepslateIronOre.id, 1))),
            node(deepslateIronOre, 32_949),
            node(cobblestone, 51_437),
        )

        val answers = PlannedFarmAnswers.of(
            plan,
            listOf(demand(deepslateIronOre, 32_949), demand(cobblestone, 51_437)),
            listOf(farm(7, "Cobble Farm", cobblestone to 51_437), farm(9, "Iron Farm", ironIngot to 32_949)),
        )

        assertEquals(setOf(cobblestone.id, deepslateIronOre.id), answers.answeredIds())
    }
}
