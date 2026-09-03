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
        val ranked = TagMemberRanking.rank(minedAgainstCrafted(), listOf(charcoal, coal), demand = 8)

        assertEquals(2, ranked.size)
        assertTrue(ranked[0].score >= ranked[1].score, "rank() must return descending score")
        ranked.forEach { assertTrue(it.bestSource != null, "${it.member.id} should have a source") }
    }

    /**
     * The tiebreak that matters. Left to an alphabetical order "Red Sand" beats "Sand", which is
     * the less canonical answer to a question nobody wants to be asked in the first place.
     */
    @Test
    fun `MemberPrior decides members the scorer rates equally`() {
        val graph = twoIdenticalBlocks(sand, redSand)

        assertEquals(sand.id, TagMemberRanking.recommended(graph, listOf(redSand, sand), demand = 4)?.id)
        // Input order must not matter — it is a ranking, not a pick-the-first.
        assertEquals(sand.id, TagMemberRanking.recommended(graph, listOf(sand, redSand), demand = 4)?.id)
    }

    @Test
    fun `a member with no source at all sorts last rather than being dropped`() {
        val graph = twoIdenticalBlocks(sand, redSand)
        val unknown = Item("minecraft:unobtainable", "Unobtainable")

        val ranked = TagMemberRanking.rank(graph, listOf(unknown, sand, redSand), demand = 4)

        assertEquals(3, ranked.size, "the picker still has to offer an unsourced member")
        assertEquals(unknown.id, ranked.last().member.id)
        assertNull(ranked.last().bestSource)
    }

    @Test
    fun `no graph means no recommendation, never a guess`() {
        assertNull(TagMemberRanking.recommended(null, listOf(sand, redSand), demand = 4))
    }

    @Test
    fun `a set of one is not a question`() {
        assertNull(TagMemberRanking.recommended(twoIdenticalBlocks(sand, redSand), listOf(sand), demand = 4))
        assertNull(TagMemberRanking.recommended(twoIdenticalBlocks(sand, redSand), emptyList(), demand = 4))
    }

    @Test
    fun `a set whose members are all unobtainable yields no recommendation`() {
        // Answering with a member nothing can produce would replace a question with a dead end.
        val empty = ItemSourceGraph.builder().build()
        val ghosts = listOf(Item("minecraft:ghost_a", "Ghost A"), Item("minecraft:ghost_b", "Ghost B"))

        assertNull(TagMemberRanking.recommended(empty, ghosts, demand = 4))
    }
}
