package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.resources.ResourceGatheringItem
import kotlin.test.Test
import kotlin.test.assertEquals

class ResumeHeroSortTest {

    private fun item(id: Int, name: String, collected: Int, required: Int = 64) = ResourceGatheringItem(
        id = id,
        projectId = 1,
        itemId = "minecraft:test",
        name = name,
        required = required,
        collected = collected,
    )

    @Test
    fun `needed first puts closest-to-done on top and completed last`() {
        val items = listOf(
            item(1, "Far", collected = 4),
            item(2, "Done", collected = 64),
            item(3, "Close", collected = 60),
        )

        val sorted = sortResumeResources(items, ResumeSort.NEEDED)

        assertEquals(listOf("Close", "Far", "Done"), sorted.map { it.name })
    }

    @Test
    fun `az sorts by name`() {
        val items = listOf(
            item(1, "Zinc", collected = 0),
            item(2, "Anvil", collected = 0),
        )

        assertEquals(listOf("Anvil", "Zinc"), sortResumeResources(items, ResumeSort.AZ).map { it.name })
    }

    @Test
    fun `progress sorts ascending`() {
        val items = listOf(
            item(1, "Half", collected = 32),
            item(2, "Empty", collected = 0),
            item(3, "Full", collected = 64),
        )

        assertEquals(
            listOf("Empty", "Half", "Full"),
            sortResumeResources(items, ResumeSort.PROGRESS).map { it.name }
        )
    }

    @Test
    fun `unknown sort param falls back to needed`() {
        assertEquals(ResumeSort.NEEDED, ResumeSort.fromParam("bogus"))
        assertEquals(ResumeSort.NEEDED, ResumeSort.fromParam(null))
        assertEquals(ResumeSort.AZ, ResumeSort.fromParam("az"))
    }
}
