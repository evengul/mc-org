package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.resources.ResourceGatheringItem
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldLogSliceTest {

    private fun item(
        id: Int,
        name: String,
        collected: Int = 0,
        required: Int = 64,
        solvedBy: Pair<Int, String>? = null,
    ) = ResourceGatheringItem(
        id = id,
        projectId = 1,
        itemId = "minecraft:test",
        name = name,
        required = required,
        collected = collected,
        solvedByProject = solvedBy,
    )

    @Test
    fun `gatherable excludes completed items`() {
        val items = listOf(
            item(1, "Done Item", collected = 64),
            item(2, "Open Item", collected = 10),
        )

        assertEquals(listOf("Open Item"), sliceGatherable(items, emptySet()).map { it.name })
    }

    @Test
    fun `gatherable excludes items waiting on a blocking producer`() {
        val items = listOf(
            item(1, "Sticky Piston", solvedBy = 7 to "Slime Farm"),
            item(2, "Smooth Stone"),
        )

        assertEquals(
            listOf("Smooth Stone"),
            sliceGatherable(items, blockedProducerIds = setOf(7)).map { it.name }
        )
    }

    @Test
    fun `items solved by a done producer stay gatherable`() {
        val items = listOf(item(1, "Hopper", solvedBy = 9 to "Iron Farm"))

        // producer 9 is done, so it is not in the blocking set
        assertEquals(listOf("Hopper"), sliceGatherable(items, emptySet()).map { it.name })
    }

    @Test
    fun `next to gather caps at five and sorts closest to done first`() {
        val items = (1..8).map { index ->
            item(index, "Item $index", collected = index * 4, required = 64)
        }

        val next = sliceNextToGather(items, emptySet())

        assertEquals(5, next.size)
        assertEquals("Item 8", next.first().name)
    }

    @Test
    fun `query filters by name case-insensitively`() {
        val items = listOf(
            item(1, "Redstone Comparator"),
            item(2, "Smooth Stone"),
            item(3, "Redstone Dust"),
        )

        val next = sliceNextToGather(items, emptySet(), query = "redstone")

        assertEquals(setOf("Redstone Comparator", "Redstone Dust"), next.map { it.name }.toSet())
    }
}
