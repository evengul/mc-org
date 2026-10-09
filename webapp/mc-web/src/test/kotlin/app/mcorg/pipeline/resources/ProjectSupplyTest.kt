package app.mcorg.pipeline.resources

import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.model.resources.ResourceSourceType
import app.mcorg.engine.plan.SupplySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The supplied-map rule, pinned where it now lives.
 *
 * It was inline in `GenerateGatheringPlanStep.process` and moved out so the drill's picker could
 * reach the same answer (MCO-523). Moving logic is where logic breaks, and this rule had no direct
 * test of its own — it was covered only through whole-plan assertions, which would still pass if
 * `manual` quietly stopped opting out of farm supply and simply produced a differently-sourced
 * plan.
 *
 * Each case below is a rule someone wrote down deliberately, not an implementation detail.
 */
class ProjectSupplyTest {

    private fun item(
        itemId: String,
        sourceType: ResourceSourceType? = null,
        solvedByProject: Pair<Int, String>? = null,
    ) = ResourceGatheringItem(
        id = 1,
        projectId = 7,
        itemId = itemId,
        name = itemId.substringAfter(':'),
        required = 64,
        collected = 0,
        solvedByProject = solvedByProject,
        sourceType = sourceType,
    )

    private fun farm(
        itemId: String,
        projectId: Int = 2,
        projectName: String = "Iron Farm",
        ratePerHour: Int = 0,
        assumed: Boolean = false,
    ) = FarmSupplyRow(
        itemId = itemId,
        projectId = projectId,
        projectName = projectName,
        ratePerHour = ratePerHour,
        assumed = assumed,
    )

    private fun suppliedBy(farms: List<FarmSupplyRow>, itemId: String = "minecraft:iron_ingot"): String =
        assertIs<SupplySource.Farm>(ProjectSupply.fold(listOf(item(itemId)), farms)[itemId]).label

    @Test
    fun `an operational project's production supplies the whole world`() {
        val supplied = ProjectSupply.fold(
            activeItems = listOf(item("minecraft:iron_ingot")),
            farms = listOf(farm("minecraft:iron_ingot")),
        )

        val supply = supplied["minecraft:iron_ingot"]
        assertIs<SupplySource.Farm>(supply)
        assertEquals("Iron Farm", supply.label)
    }

    @Test
    fun `a manual source pick opts the item out of farm supply`() {
        // The point of MANUAL: "I am gathering this one myself, do not tell me the farm has it."
        // Without the opt-out the item terminates as SUPPLIED and disappears from the plan.
        val supplied = ProjectSupply.fold(
            activeItems = listOf(item("minecraft:iron_ingot", sourceType = ResourceSourceType.MANUAL)),
            farms = listOf(farm("minecraft:iron_ingot")),
        )

        assertNull(supplied["minecraft:iron_ingot"], "a manual pick must not be farm-supplied")
    }

    @Test
    fun `the opt-out is per item, not per project`() {
        val supplied = ProjectSupply.fold(
            activeItems = listOf(
                item("minecraft:iron_ingot", sourceType = ResourceSourceType.MANUAL),
                item("minecraft:redstone"),
            ),
            farms = listOf(farm("minecraft:iron_ingot"), farm("minecraft:redstone", projectName = "Redstone Farm")),
        )

        assertNull(supplied["minecraft:iron_ingot"])
        assertIs<SupplySource.Farm>(supplied["minecraft:redstone"])
    }

    @Test
    fun `an explicit project link replaces a farm entry for the same item`() {
        // Union order is the rule: linked project wins over farm. A user who said "this comes from
        // project 9" gets project 9, not whichever farm also happens to make it.
        val supplied = ProjectSupply.fold(
            activeItems = listOf(item("minecraft:iron_ingot", solvedByProject = 9 to "Ingot Depot")),
            farms = listOf(farm("minecraft:iron_ingot")),
        )

        val supply = supplied["minecraft:iron_ingot"]
        assertIs<SupplySource.LinkedProject>(supply)
        assertEquals(9, supply.projectId)
        assertEquals("Ingot Depot", supply.label)
    }

    @Test
    fun `a link survives even when the item is also marked manual`() {
        // MANUAL only suppresses *farm* supply. An explicit link is a stronger statement than the
        // absence of one, so it is applied over the top rather than being filtered out with it.
        val supplied = ProjectSupply.fold(
            activeItems = listOf(
                item(
                    "minecraft:iron_ingot",
                    sourceType = ResourceSourceType.MANUAL,
                    solvedByProject = 9 to "Ingot Depot",
                )
            ),
            farms = listOf(farm("minecraft:iron_ingot")),
        )

        assertIs<SupplySource.LinkedProject>(supplied["minecraft:iron_ingot"])
    }

    @Test
    fun `of several producers of one item, the fastest supplies it`() {
        // MCO-603: the plan names the farm that finishes soonest. It used to name the first by
        // project name, so Bartering setup out-ranked a dedicated farm twice its speed.
        val farms = listOf(
            farm("minecraft:iron_ingot", projectId = 2, projectName = "Alpha Farm", ratePerHour = 400),
            farm("minecraft:iron_ingot", projectId = 3, projectName = "Beta Farm", ratePerHour = 3_800),
        )

        assertEquals("Beta Farm", suppliedBy(farms))
    }

    @Test
    fun `an unmeasured rate loses to any measured one`() {
        // An import writes an unmeasured rate as 0. Read as a number it would simply be the
        // slowest, which is the right answer: a farm nobody timed is not a reason to skip one
        // somebody did.
        val farms = listOf(
            farm("minecraft:iron_ingot", projectId = 2, projectName = "Alpha Farm", ratePerHour = 0),
            farm("minecraft:iron_ingot", projectId = 3, projectName = "Beta Farm", ratePerHour = 1),
        )

        assertEquals("Beta Farm", suppliedBy(farms))
    }

    @Test
    fun `producers at the same rate reduce to a single deterministic farm, the first by name`() {
        // GetWorldFarmSuppliesStep orders by project name precisely so this pick is stable; two
        // renders of the same plan must not disagree about which farm is named. The tie keeps the
        // query's order rather than re-sorting in Kotlin, so it follows the database's collation —
        // the same one the roadmap's edge query compares names with.
        val farms = listOf(
            farm("minecraft:ender_pearl", projectId = 2, projectName = "Bartering setup", ratePerHour = 1_000),
            farm("minecraft:ender_pearl", projectId = 3, projectName = "Ender ender", ratePerHour = 1_000),
        )

        repeat(3) { assertEquals("Bartering setup", suppliedBy(farms, "minecraft:ender_pearl")) }
    }

    @Test
    fun `a running farm keeps an item an assumed farm would make faster`() {
        // The roadmap's "promised" split derives a plan as if unbuilt farms were finished. An item a
        // running farm already makes is supplied either way, so it must keep the running farm's
        // name; otherwise the split would count it as promised by a farm nobody has built.
        val farms = listOf(
            farm("minecraft:iron_ingot", projectId = 2, projectName = "Iron Farm", ratePerHour = 400),
            farm("minecraft:iron_ingot", projectId = 3, projectName = "Big Iron Farm", ratePerHour = 3_800, assumed = true),
        )

        assertEquals("Iron Farm", suppliedBy(farms))
    }

    @Test
    fun `nothing supplied yields an empty map rather than a null-ish one`() {
        assertTrue(ProjectSupply.fold(listOf(item("minecraft:iron_ingot")), emptyList()).isEmpty())
    }
}
