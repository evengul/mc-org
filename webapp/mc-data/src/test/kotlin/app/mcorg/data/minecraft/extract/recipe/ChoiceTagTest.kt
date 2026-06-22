package app.mcorg.data.minecraft.extract.recipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChoiceTagTest {

    @Test
    fun `id is deterministic and order-independent`() {
        val a = choiceTag(listOf("minecraft:sand", "minecraft:red_sand"))
        val b = choiceTag(listOf("minecraft:red_sand", "minecraft:sand"))
        assertEquals("#mcorg:choice/red_sand_sand", a.id)
        assertEquals(a.id, b.id, "the same alternative set must produce the same tag id")
    }

    @Test
    fun `name and members derive from the sorted ids`() {
        val tag = choiceTag(listOf("minecraft:sand", "minecraft:red_sand"))
        assertEquals("Red Sand or Sand", tag.name)
        assertEquals(listOf("minecraft:red_sand", "minecraft:sand"), tag.content.map { it.id })
    }

    @Test
    fun `duplicate alternatives collapse`() {
        val tag = choiceTag(listOf("minecraft:sand", "minecraft:sand"))
        assertEquals("#mcorg:choice/sand", tag.id)
        assertEquals(1, tag.content.size)
    }

    @Test
    fun `a large alternative set falls back to a hashed id and summarised name that fit the column`() {
        val colors = listOf(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
        )
        val members = colors.map { "minecraft:${it}_wool" }
        val tag = choiceTag(members)

        assertTrue(tag.id.length <= 100, "id length ${tag.id.length} must fit VARCHAR(100): ${tag.id}")
        assertTrue(tag.name.length <= 100, "name length ${tag.name.length} must fit VARCHAR(100): ${tag.name}")
        assertTrue(tag.id.startsWith("#mcorg:choice/"))
        assertEquals(16, tag.content.size)
        assertEquals(tag.id, choiceTag(members.reversed()).id, "hashed id must still be deterministic")
    }
}
