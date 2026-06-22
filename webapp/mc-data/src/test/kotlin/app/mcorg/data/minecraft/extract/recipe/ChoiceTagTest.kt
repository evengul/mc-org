package app.mcorg.data.minecraft.extract.recipe

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
