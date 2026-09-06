package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.ItemSourceGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-507 — the single definition of "recommended" for an open tag's members.
 *
 * The picker and the bulk "answer the remaining N" action both read this. These tests pin the
 * three things that would let them disagree with each other or with the engine: that the order
 * comes from the source score, that [app.mcorg.engine.plan.MemberPrior] breaks the ties the
 * scorer cannot, and that "no recommendation" is a real answer rather than a guess.
 */
class TagMemberRankingTest {

    private val sand = Item("minecraft:sand", "Sand")
    private val redSand = Item("minecraft:red_sand", "Red Sand")
    private val coal = Item("minecraft:coal", "Coal")
    private val charcoal = Item("minecraft:charcoal", "Charcoal")

    /** Two raw-gather blocks, structurally identical — the scorer genuinely cannot separate them. */
    private fun twoIdenticalBlocks(a: MinecraftId, b: MinecraftId): ItemSourceGraph {
        val builder = ItemSourceGraph.builder()
        listOf(a, b).forEach { item ->
            val block = builder.addSourceNode(
                ResourceSource.SourceType.LootTypes.BLOCK,
                "blocks/${item.id.substringAfter(':')}.json",
            )
            builder.addSourceToItemEdge(block, builder.addItemNode(item), 1)
        }
        return builder.build()
    }

    /**
     * [coal] mined from a block, [charcoal] crafted from a log — different structures, so the
     * scorer has something to say and the prior never gets to speak.
     */
    private fun minedAgainstCrafted(): ItemSourceGraph {
        val builder = ItemSourceGraph.builder()
        val log = Item("minecraft:oak_log", "Oak Log")

        val ore = builder.addSourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/coal_ore.json")
        builder.addSourceToItemEdge(ore, builder.addItemNode(coal), 1)

        val logBlock = builder.addSourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/oak_log.json")
        builder.addSourceToItemEdge(logBlock, builder.addItemNode(log), 1)

        val smelt = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.SMELTING, "charcoal.json")
        builder.addSourceToItemEdge(smelt, builder.addItemNode(charcoal), 1)
        builder.addItemToSourceEdge(builder.addItemNode(log), smelt, 1)

        return builder.build()
    }

    @Test
    fun `every member is ranked, best first, with its best source attached`() {
        val ranked = TagMemberRanking.rank(minedAgainstCrafted(), listOf(charcoal, coal))

        assertEquals(2, ranked.size)
        // Ascending, and that inversion is the point of MCO-521: this is a cost in minutes now,
        // so the *lowest* number wins where the highest used to.
        assertTrue(ranked[0].cost <= ranked[1].cost, "rank() must return ascending cost")
        ranked.forEach { assertTrue(it.bestSource != null, "${it.member.id} should have a source") }
    }

    /**
     * The tiebreak that matters. Left to an alphabetical order "Red Sand" beats "Sand", which is
     * the less canonical answer to a question nobody wants to be asked in the first place.
     */
    @Test
    fun `MemberPrior decides members the model prices equally`() {
        val graph = twoIdenticalBlocks(sand, redSand)

        assertEquals(sand.id, TagMemberRanking.recommended(graph, listOf(redSand, sand))?.id)
        // Input order must not matter — it is a ranking, not a pick-the-first.
        assertEquals(sand.id, TagMemberRanking.recommended(graph, listOf(sand, redSand))?.id)
    }

    @Test
    fun `a member with no source at all sorts last rather than being dropped`() {
        val graph = twoIdenticalBlocks(sand, redSand)
        val unknown = Item("minecraft:unobtainable", "Unobtainable")

        val ranked = TagMemberRanking.rank(graph, listOf(unknown, sand, redSand))

        assertEquals(3, ranked.size, "the picker still has to offer an unsourced member")
        assertEquals(unknown.id, ranked.last().member.id)
        assertNull(ranked.last().bestSource)
        // The sentinel inverted with the ordering (MCO-521). It sorted last at Int.MIN_VALUE, the
        // smallest value; it now sorts last at UNREACHABLE, the largest. Reusing the old sentinel
        // under an ascending sort would have put every unobtainable member first.
        assertTrue(ranked.last().unpriceable, "an unsourced member must be unpriceable, not cheap")
    }

    @Test
    fun `no graph means no recommendation, never a guess`() {
        assertNull(TagMemberRanking.recommended(null, listOf(sand, redSand)))
    }

    @Test
    fun `a set of one is not a question`() {
        assertNull(TagMemberRanking.recommended(twoIdenticalBlocks(sand, redSand), listOf(sand)))
        assertNull(TagMemberRanking.recommended(twoIdenticalBlocks(sand, redSand), emptyList()))
    }

    @Test
    fun `a set whose members are all unobtainable yields no recommendation`() {
        // Answering with a member nothing can produce would replace a question with a dead end.
        val empty = ItemSourceGraph.builder().build()
        val ghosts = listOf(Item("minecraft:ghost_a", "Ghost A"), Item("minecraft:ghost_b", "Ghost B"))

        assertNull(TagMemberRanking.recommended(empty, ghosts))
    }
}
