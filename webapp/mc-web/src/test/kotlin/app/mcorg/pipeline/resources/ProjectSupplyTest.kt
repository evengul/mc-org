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

    private fun farm(itemId: String, projectId: Int = 2, projectName: String = "Iron Farm") =
        FarmSupplyRow(itemId = itemId, projectId = projectId, projectName = projectName)

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
    fun `several producers of one item reduce to a single deterministic farm`() {
        // GetWorldFarmSuppliesStep orders by project name precisely so this pick is stable; two
        // renders of the same plan must not disagree about which farm is named.
        val farms = listOf(
            farm("minecraft:iron_ingot", projectId = 2, projectName = "Alpha Farm"),
            farm("minecraft:iron_ingot", projectId = 3, projectName = "Beta Farm"),
        )

        repeat(3) {
            val supply = ProjectSupply.fold(listOf(item("minecraft:iron_ingot")), farms)["minecraft:iron_ingot"]
            assertEquals("Alpha Farm", assertIs<SupplySource.Farm>(supply).label)
        }
    }

    @Test
    fun `nothing supplied yields an empty map rather than a null-ish one`() {
        assertTrue(ProjectSupply.fold(listOf(item("minecraft:iron_ingot")), emptyList()).isEmpty())
    }
}
