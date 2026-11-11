package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.resources.ResourceGatheringItem
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Below 768 px `.data-table` stacks each row into a card and labels every cell from its
 * `data-label` (data-table.css). A cell without one is an unlabelled line in the card (MCO-622).
 */
class PlanResourceTableLabelTest {

    private fun row(id: Int, ignored: Boolean = false) = ResourceGatheringItem(
        id = id,
        projectId = 1,
        itemId = "minecraft:iron_ingot",
        name = "Iron Ingot $id",
        required = 64,
        collected = 0,
        ignored = ignored,
    )

    @Test
    fun `every cell of the active and the ignored table carries a data-label`() {
        val html = createHTML().div {
            planResourcesArea(worldId = 1, projectId = 1, resources = listOf(row(1), row(2), row(3, ignored = true)), plan = null)
        }

        val cells = Regex("<td[^>]*>").findAll(html).map { it.value }.toList()
        assertEquals(12, cells.size, "three rows of four cells")
        val unlabelled = cells.filterNot { it.contains("data-label=\"") }
        assertTrue(unlabelled.isEmpty(), "cells without data-label: $unlabelled")
        listOf("Item", "Qty", "Chests", "Actions").forEach { label ->
            assertEquals(3, cells.count { it.contains("data-label=\"$label\"") }, "one $label per row")
        }
    }
}
