package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.world.World
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanRequirement
import app.mcorg.engine.plan.PlanTarget
import app.mcorg.engine.plan.SupplySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-294 — which designs in the bank answer this plan.
 *
 * The cases here are the ones measured on the real dogfood world (see the spike on MCO-294),
 * not invented ones: the iron chain the roll-up cannot see, the witch hut that produces three
 * separate demands, and the oak log whose demand no single design can claim.
 */
class FarmSuggestionsTest {

    private val ironIngot = Item("minecraft:iron_ingot", "Iron Ingot")
    private val deepslateIronOre = Item("minecraft:deepslate_iron_ore", "Deepslate Iron Ore")
    private val redstone = Item("minecraft:redstone", "Redstone Dust")
    private val stick = Item("minecraft:stick", "Stick")
    private val glassBottle = Item("minecraft:glass_bottle", "Glass Bottle")
    private val oakLog = Item("minecraft:oak_log", "Oak Log")
    private val planks = Item("minecraft:oak_planks", "Oak Planks")
    private val cobblestone = Item("minecraft:cobblestone", "Cobblestone")
    private val anyPlanks = MinecraftTag("#minecraft:planks", "Planks", emptyList())

    private val threshold = World.DEFAULT_FARM_SCALE_THRESHOLD // 1,728

    // ---- fixtures ------------------------------------------------------------------

    private fun plan(vararg nodes: PlanNode) = GatheringPlan(
        nodes = nodes.associateBy { it.item.id },
        targets = listOf(PlanTarget(nodes.first().item, nodes.first().quantity)),
    )

    private fun node(
        item: MinecraftId,
        quantity: Long,
        status: PlanNodeStatus = PlanNodeStatus.RAW_GATHER,
        requires: List<PlanRequirement> = emptyList(),
        supply: SupplySource? = null,
    ) = PlanNode(
        item = item, quantity = quantity, crafts = 0, leftover = 0,
        status = status, supply = supply, requires = requires,
    )

    private fun producer(id: Int, name: String, vararg rates: Pair<MinecraftId, Int?>) =
        IdeaProducer(id, name, rates.associate { (item, rate) -> item.id to rate })

    // ---- the case the roll-up cannot see -------------------------------------------

    @Test
    fun `a design matches demand the roll-up never lists`() {
        // Measured: the roll-up's 4th line is Deepslate Iron Ore because the plan mines ore,
        // while the design produces ingots. Keyed on RAW_GATHER leaves this matches nothing.
        val ironPlan = plan(
            node(ironIngot, 33_049, PlanNodeStatus.RESOLVED, listOf(PlanRequirement("minecraft:deepslate_iron_ore", 1))),
            node(deepslateIronOre, 33_049),
        )

        val result = FarmSuggestions.of(ironPlan, threshold, listOf(producer(1, "8 Pod Iron Farm", ironIngot to 3810)))

        assertEquals(1, result.size)
        val suggestion = result.single()
        assertEquals(listOf("minecraft:iron_ingot" to 33_049L), suggestion.produces.map { it.itemId to it.quantity })
        assertEquals(3810, suggestion.produces.single().ratePerHour)
    }

    @Test
    fun `the mining underneath a matched item counts as removed`() {
        val ironPlan = plan(
            node(ironIngot, 33_049, PlanNodeStatus.RESOLVED, listOf(PlanRequirement("minecraft:deepslate_iron_ore", 1))),
            node(deepslateIronOre, 33_049),
        )

        val suggestion = FarmSuggestions
            .of(ironPlan, threshold, listOf(producer(1, "8 Pod Iron Farm", ironIngot to 3810)))
            .single()

        assertEquals(
            listOf("minecraft:deepslate_iron_ore" to 33_049L),
            suggestion.alsoRemoves.map { it.itemId to it.quantity },
            "building the farm means the ore is not mined either",
        )
        assertEquals(66_098, suggestion.unitsRemoved)
        // The rate belongs to what the design makes; the ore is a consequence, not an output.
        assertEquals(null, suggestion.alsoRemoves.single().ratePerHour)
    }

    @Test
    fun `demand feeding something the design does not cover is left alone`() {
        // Oak logs feed both the planks and the sticks. A design covering only sticks cannot
        // claim the logs — apportioning that split is exactly what this does not attempt.
        val woodPlan = plan(
            node(planks, 40_000, PlanNodeStatus.RESOLVED, listOf(PlanRequirement("minecraft:oak_log", 1))),
            node(stick, 21_939, PlanNodeStatus.RESOLVED, listOf(PlanRequirement("minecraft:oak_log", 1))),
            node(oakLog, 31_267),
        )

        val suggestion = FarmSuggestions
            .of(woodPlan, threshold, listOf(producer(1, "Witch Hut Farm", stick to 1580)))
            .single()

        assertTrue(
            suggestion.alsoRemoves.none { it.itemId == "minecraft:oak_log" },
            "the planks still need the logs, so the logs are not removed: ${suggestion.alsoRemoves}",
        )
        assertEquals(21_939, suggestion.unitsRemoved)
    }

    // ---- one line per design, not per item -----------------------------------------

    @Test
    fun `a design producing three demanded items is suggested once`() {
        // The real shape of the witch hut: redstone is why you build it, sticks and glass
        // bottles come along. Three lines here would invite building it "for sticks".
        val witchPlan = plan(
            node(redstone, 63_273),
            node(stick, 21_939),
            node(glassBottle, 216),
        )

        val result = FarmSuggestions.of(
            witchPlan,
            threshold,
            listOf(producer(1, "Witch Hut Farm", redstone to 8280, stick to 1580, glassBottle to 785)),
        )

        assertEquals(1, result.size)
        assertEquals(
            listOf("minecraft:redstone", "minecraft:stick", "minecraft:glass_bottle"),
            result.single().produces.map { it.itemId },
            "everything it covers, largest first, on one suggestion",
        )
    }

    @Test
    fun `a design is not suggested when nothing it makes is farm-scale`() {
        val smallPlan = plan(node(glassBottle, 216))

        val result = FarmSuggestions.of(smallPlan, threshold, listOf(producer(1, "Witch Hut Farm", glassBottle to 785)))

        assertTrue(result.isEmpty(), "216 bottles is not a reason to build a farm")
    }

    @Test
    fun `sub-threshold items ride along once the design qualifies`() {
        val witchPlan = plan(node(redstone, 63_273), node(glassBottle, 216))

        val suggestion = FarmSuggestions
            .of(witchPlan, threshold, listOf(producer(1, "Witch Hut Farm", redstone to 8280, glassBottle to 785)))
            .single()

        assertEquals(
            listOf("minecraft:redstone", "minecraft:glass_bottle"),
            suggestion.produces.map { it.itemId },
            "the bottles are not a reason to build it, but they are a reason to know about it",
        )
    }

    // ---- what must never be suggested ----------------------------------------------

    @Test
    fun `demand an operational farm already supplies is not suggested again`() {
        val suppliedPlan = plan(
            node(cobblestone, 74_557, PlanNodeStatus.SUPPLIED, supply = SupplySource.Farm("Cobble farm")),
        )

        val result = FarmSuggestions.of(
            suppliedPlan,
            threshold,
            listOf(producer(1, "231k Cobblestone farm", cobblestone to 924_000)),
        )

        assertTrue(result.isEmpty(), "it is already solved — this is MCO-401's rule, kept here")
    }

    @Test
    fun `an unresolved tag matches nothing`() {
        // A tag is not an item and carries no id a design could produce. Resolving it turns it
        // into real demand this then sees (MCO-400).
        val taggedPlan = plan(node(anyPlanks, 121_774, PlanNodeStatus.OPEN_TAG))

        val result = FarmSuggestions.of(taggedPlan, threshold, listOf(producer(1, "Tree farm", planks to 5000)))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a blocked item is still worth suggesting a farm for`() {
        // No feasible source in the graph is the strongest possible reason to want a design.
        val blockedPlan = plan(node(redstone, 63_273, PlanNodeStatus.BLOCKED))

        val result = FarmSuggestions.of(blockedPlan, threshold, listOf(producer(1, "Witch Hut Farm", redstone to 8280)))

        assertEquals(1, result.size)
    }

    // ---- ordering and rates ---------------------------------------------------------

    @Test
    fun `designs are ranked by the work they remove`() {
        val bigPlan = plan(
            node(cobblestone, 75_151),
            node(redstone, 63_273),
        )

        val result = FarmSuggestions.of(
            bigPlan,
            threshold,
            listOf(
                producer(1, "Witch Hut Farm", redstone to 8280),
                producer(2, "Cobblestone farm", cobblestone to 924_000),
            ),
        )

        assertEquals(listOf("Cobblestone farm", "Witch Hut Farm"), result.map { it.ideaName })
    }

    @Test
    fun `an unmeasured rate suggests the design without inventing a number`() {
        // rate_per_hour is nullable precisely so a design can be shared without timing it.
        val bambooPlan = plan(node(stick, 21_939))

        val suggestion = FarmSuggestions
            .of(bambooPlan, threshold, listOf(producer(1, "Bamboo farm", stick to null)))
            .single()

        assertEquals(null, suggestion.produces.single().ratePerHour)
        assertEquals(null, suggestion.produces.single().hoursToCover)
    }

    @Test
    fun `hours to cover comes from the rate`() {
        val redstonePlan = plan(node(redstone, 63_273))

        val suggestion = FarmSuggestions
            .of(redstonePlan, threshold, listOf(producer(1, "Witch Hut Farm", redstone to 8280)))
            .single()

        val hours = suggestion.produces.single().hoursToCover!!
        assertTrue(hours > 7.6 && hours < 7.7, "63,273 at 8,280/h is about 7.6 hours, got $hours")
    }

    // ---- what the world already covers (MCO-458, MCO-461) ---------------------------

    @Test
    fun `a design for what this project itself produces is not suggested`() {
        // The cobblestone farm costs 4,000 cobblestone to build, and was offered a cobblestone
        // farm. Recorded by hand through MCO-298, it has no source design to exclude by id —
        // its own productions are the only thing that says what it makes.
        val theFarmsOwnPlan = plan(node(cobblestone, 4_000))

        val result = FarmSuggestions.of(
            theFarmsOwnPlan,
            threshold,
            listOf(producer(1, "231k Cobblestone farm", cobblestone to 231_000)),
            alreadyCovered = setOf(cobblestone.id),
        )

        assertTrue(result.isEmpty(), "you cannot need what you produce")
    }

    @Test
    fun `a bigger design for a covered item is excluded too`() {
        // Deliberate, not an oversight: 924,000/h against your 231,000/h is a real answer to
        // demand the small farm cannot meet, but not one worth reading as "build a second
        // cobble farm". Excluded rather than ranked down - Even's call, 2026-08-25.
        val cobblePlan = plan(node(cobblestone, 75_151))

        val result = FarmSuggestions.of(
            cobblePlan,
            threshold,
            listOf(producer(1, "924k Cobblestone farm", cobblestone to 924_000)),
            alreadyCovered = setOf(cobblestone.id),
        )

        assertTrue(result.isEmpty(), "the upgrade case needs a rate comparison nobody has asked for")
    }

    @Test
    fun `a design still qualifies on what the world does not cover`() {
        val witchPlan = plan(node(redstone, 63_273), node(cobblestone, 75_151))

        val suggestion = FarmSuggestions
            .of(
                witchPlan,
                threshold,
                listOf(producer(1, "Witch Hut Farm", redstone to 8280, cobblestone to 100)),
                alreadyCovered = setOf(cobblestone.id),
            )
            .single()

        assertEquals(listOf("minecraft:redstone"), suggestion.produces.map { it.itemId })
        assertEquals(63_273, suggestion.unitsRemoved, "covered cobblestone is not work this removes")
    }

    @Test
    fun `a covered item does not qualify a design on its own`() {
        // Cobblestone is the only farm-scale line and the world already covers it, so the
        // ride-along rule must not resurrect the design on the sub-threshold bottles.
        val mixedPlan = plan(node(cobblestone, 75_151), node(glassBottle, 216))

        val result = FarmSuggestions.of(
            mixedPlan,
            threshold,
            listOf(producer(1, "Cobble and bottles", cobblestone to 924_000, glassBottle to 785)),
            alreadyCovered = setOf(cobblestone.id),
        )

        assertTrue(result.isEmpty(), "216 bottles is still not a reason to build a farm")
    }

    @Test
    fun `the mining under a covered item is not claimed either`() {
        val ironPlan = plan(
            node(ironIngot, 33_049, PlanNodeStatus.RESOLVED, listOf(PlanRequirement("minecraft:deepslate_iron_ore", 1))),
            node(deepslateIronOre, 33_049),
            node(redstone, 63_273),
        )

        val suggestion = FarmSuggestions
            .of(
                ironPlan,
                threshold,
                listOf(producer(1, "Iron and redstone", ironIngot to 3810, redstone to 8280)),
                alreadyCovered = setOf(ironIngot.id),
            )
            .single()

        assertEquals(listOf("minecraft:redstone"), suggestion.produces.map { it.itemId })
        assertTrue(
            suggestion.alsoRemoves.none { it.itemId == "minecraft:deepslate_iron_ore" },
            "the ore feeds ingots the world already covers, so this design does not remove it: ${suggestion.alsoRemoves}",
        )
    }

    @Test
    fun `an empty bank suggests nothing`() {
        assertEquals(emptyList(), FarmSuggestions.of(plan(node(cobblestone, 75_151)), threshold, emptyList()))
    }
}
