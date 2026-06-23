package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The member prior is a tiebreak over the alternatives of one choice set. Each test sorts a real
 * choice set (taken from the 1.21.10 recipe corpus) by the prior and asserts the canonical member
 * lands first — that being the bug this fixes (alphabetical order surfaced the *less* canonical one).
 */
class MemberPriorTest {

    private fun sortedByPrior(vararg ids: String): List<String> =
        ids.sortedWith { a, b -> MemberPrior.compare(a, b) }

    private fun mc(local: String): String = "minecraft:$local"

    @Test
    fun `sand beats red sand — the motivating TNT case`() {
        assertEquals(
            listOf(mc("sand"), mc("red_sand")),
            sortedByPrior(mc("red_sand"), mc("sand")),
        )
    }

    @Test
    fun `oak is the default plank species`() {
        val planks = listOf(
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks",
            "dark_oak_planks", "pale_oak_planks", "crimson_planks", "warped_planks",
            "mangrove_planks", "bamboo_planks", "cherry_planks",
        ).map { mc(it) }
        val first = planks.sortedWith { a, b -> MemberPrior.compare(a, b) }.first()
        assertEquals(mc("oak_planks"), first)
    }

    @Test
    fun `dark_oak does not get mistaken for oak`() {
        // 'oak' is a substring of 'dark_oak'; the longer name must win the species match so
        // dark_oak ranks as its own (later) species, not as plain oak.
        assertEquals(
            listOf(mc("oak_planks"), mc("dark_oak_planks")),
            sortedByPrior(mc("dark_oak_planks"), mc("oak_planks")),
        )
    }

    @Test
    fun `plain log beats wood and stripped forms within a species`() {
        assertEquals(
            listOf(mc("oak_log"), mc("oak_wood"), mc("stripped_oak_log"), mc("stripped_oak_wood")),
            sortedByPrior(mc("stripped_oak_wood"), mc("oak_wood"), mc("stripped_oak_log"), mc("oak_log")),
        )
    }

    @Test
    fun `species outranks form across the logs set`() {
        // #minecraft:logs varies in BOTH species and form. Canonical species wins even when it
        // appears in a less canonical form: stripped_oak_log beats plain spruce_log.
        assertEquals(
            listOf(mc("stripped_oak_log"), mc("spruce_log")),
            sortedByPrior(mc("spruce_log"), mc("stripped_oak_log")),
        )
    }

    @Test
    fun `white is the default colour`() {
        val wool = listOf(
            "white_wool", "orange_wool", "magenta_wool", "light_blue_wool", "yellow_wool",
            "lime_wool", "pink_wool", "gray_wool", "light_gray_wool", "cyan_wool", "purple_wool",
            "blue_wool", "brown_wool", "green_wool", "red_wool", "black_wool",
        ).map { mc(it) }
        val first = wool.sortedWith { a, b -> MemberPrior.compare(a, b) }.first()
        assertEquals(mc("white_wool"), first)
    }

    @Test
    fun `uncoloured base beats every dyed variant`() {
        // shulker_boxes / bundles include the plain uncoloured member; it is the true base.
        assertEquals(
            listOf(mc("shulker_box"), mc("red_shulker_box")),
            sortedByPrior(mc("red_shulker_box"), mc("shulker_box")),
        )
        assertEquals(mc("bundle"), sortedByPrior(mc("black_bundle"), mc("bundle"), mc("white_bundle")).first())
    }

    @Test
    fun `light_gray is not mistaken for gray, and blackstone is not colour-black`() {
        // 'gray' is a substring of 'light_gray' — anchored "<colour>_" prefix keeps them distinct.
        assertEquals(
            listOf(mc("gray_wool"), mc("light_gray_wool")),
            sortedByPrior(mc("light_gray_wool"), mc("gray_wool")),
        )
        // 'blackstone' must not read as colour black; it is a stone base, ranked by that axis.
        assertEquals(
            listOf(mc("cobblestone"), mc("cobbled_deepslate"), mc("blackstone")),
            sortedByPrior(mc("blackstone"), mc("cobblestone"), mc("cobbled_deepslate")),
        )
    }

    @Test
    fun `coal is the default fuel at a tie`() {
        assertEquals(
            listOf(mc("coal"), mc("charcoal")),
            sortedByPrior(mc("charcoal"), mc("coal")),
        )
    }

    @Test
    fun `decorative base form beats cut, chiseled and pillar`() {
        assertEquals(
            listOf(mc("sandstone"), mc("cut_sandstone"), mc("chiseled_sandstone")),
            sortedByPrior(mc("chiseled_sandstone"), mc("cut_sandstone"), mc("sandstone")),
        )
        assertEquals(
            listOf(mc("quartz_block"), mc("quartz_pillar")),
            sortedByPrior(mc("quartz_pillar"), mc("quartz_block")),
        )
    }

    @Test
    fun `egg beats coloured eggs and soul_sand beats soul_soil`() {
        // egg (uncoloured) first; among the dyed ones the colour axis orders blue (11) before brown (12).
        assertEquals(
            listOf(mc("egg"), mc("blue_egg"), mc("brown_egg")),
            sortedByPrior(mc("brown_egg"), mc("blue_egg"), mc("egg")),
        )
        assertEquals(
            listOf(mc("soul_sand"), mc("soul_soil")),
            sortedByPrior(mc("soul_soil"), mc("soul_sand")),
        )
    }

    @Test
    fun `unrelated items with no applicable axis compare as equal`() {
        assertEquals(0, MemberPrior.compare(mc("gunpowder"), mc("diamond")))
    }

    // --- Tiebreak semantics: the prior must only ever break score ties, never override score. ---

    private data class Member(val id: MinecraftId, val score: Int)

    private fun rank(members: List<Member>): List<String> =
        members.sortedWith(
            compareByDescending<Member> { it.score }
                .then(MemberPrior.comparator { it.id })
                .thenBy { it.id.name }
        ).map { it.id.id }

    @Test
    fun `score dominates the prior`() {
        // red_sand is more canonical-losing by prior, but if it scores higher it must still win:
        // the prior is a tiebreak, not an override (e.g. charcoal winning at bulk demand).
        val ranked = rank(
            listOf(
                Member(Item(mc("sand"), "Sand"), score = 10),
                Member(Item(mc("red_sand"), "Red Sand"), score = 50),
            )
        )
        assertEquals(listOf(mc("red_sand"), mc("sand")), ranked)
    }

    @Test
    fun `prior breaks the tie when scores are equal`() {
        val ranked = rank(
            listOf(
                Member(Item(mc("red_sand"), "Red Sand"), score = 30),
                Member(Item(mc("sand"), "Sand"), score = 30),
            )
        )
        assertEquals(listOf(mc("sand"), mc("red_sand")), ranked)
    }

    @Test
    fun `name breaks a tie only when the prior is also equal`() {
        // Two ids the prior cannot separate fall through to the alphabetical name tiebreak.
        val ranked = rank(
            listOf(
                Member(Item(mc("zebra"), "Zebra"), score = 5),
                Member(Item(mc("apple"), "Apple"), score = 5),
            )
        )
        assertEquals(listOf(mc("apple"), mc("zebra")), ranked)
    }

    @Test
    fun `the previous alphabetical-only ordering was backwards for sand`() {
        // Documents the bug: name-only sort put Red Sand first; the prior now corrects it.
        val byNameOnly = listOf(
            Member(Item(mc("sand"), "Sand"), score = 30),
            Member(Item(mc("red_sand"), "Red Sand"), score = 30),
        ).sortedBy { it.id.name }.map { it.id.id }
        assertTrue(byNameOnly.first() == mc("red_sand"))
    }
}
