package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.ItemSourceGraph
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-486: two tags with the same members are one question.
 *
 * The plan used to ask "coal or charcoal" twice — once as `#minecraft:coals` (campfire's
 * ingredient) and once as `#mcorg:choice/charcoal_coal` (torch's inline list) — and answering one
 * left the other open, because [PlanOverrides.tagMember] is keyed by tag id.
 */
class TagIdentityTest {

    private val block = ResourceSource.SourceType.LootTypes.BLOCK

    private fun item(name: String) = Item("minecraft:$name", name)

    private val coal = item("coal")
    private val charcoal = item("charcoal")
    private val torch = item("torch")
    private val campfire = item("campfire")

    private val coalMembers = listOf(coal, charcoal)

    /** Vanilla's name for the set. */
    private val coalsTag = MinecraftTag("#minecraft:coals", "Coals", coalMembers)

    /** Extraction's name for the same set, from a recipe that spelled the alternatives inline. */
    private val choiceTag = MinecraftTag("#mcorg:choice/charcoal_coal", "Charcoal or Coal", coalMembers)

    private class Fixture {
        private val builder = ItemSourceGraph.builder()

        fun recipe(filename: String, output: Pair<MinecraftId, Int>, vararg inputs: Pair<MinecraftId, Int>) {
            val node = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, filename)
            builder.addSourceToItemEdge(node, builder.addItemNode(output.first), output.second)
            for ((input, quantity) in inputs) {
                builder.addItemToSourceEdge(builder.addItemNode(input), node, quantity)
            }
        }

        fun loot(type: ResourceSource.SourceType, filename: String, output: Pair<MinecraftId, Int>) {
            val node = builder.addSourceNode(type, filename)
            builder.addSourceToItemEdge(node, builder.addItemNode(output.first), output.second)
        }

        fun build(): ItemSourceGraph = builder.build()
    }

    /**
     * torch wants the synthetic name for {coal, charcoal}; campfire wants the vanilla one.
     * Both members are obtainable, so nothing but the tag choice is unresolved.
     */
    private fun twoNamesGraph(): ItemSourceGraph = Fixture().apply {
        loot(block, "blocks/coal_ore.json", coal to 1)
        loot(block, "blocks/charcoal.json", charcoal to 1)
        recipe("torch.json", torch to 4, choiceTag to 1)
        recipe("campfire.json", campfire to 1, coalsTag to 1)
    }.build()

    @Test
    fun `two tags with identical members are one question`() {
        val dag = PlanSelector.select(twoNamesGraph(), listOf(PlanTarget(torch, 4), PlanTarget(campfire, 1)))

        val open = dag.nodes.values.filter { it.status == PlanNodeStatus.OPEN_TAG }
        assertEquals(1, open.size, "the same set under two names must be asked once: $open")
        assertEquals("#minecraft:coals", open.single().item.id)
        assertNull(dag.nodes["#mcorg:choice/charcoal_coal"])
    }

    @Test
    fun `both consumers point at the one question`() {
        val dag = PlanSelector.select(twoNamesGraph(), listOf(PlanTarget(torch, 4), PlanTarget(campfire, 1)))

        assertEquals("#minecraft:coals", dag.nodes.getValue("minecraft:torch").requires.single().itemId)
        assertEquals("#minecraft:coals", dag.nodes.getValue("minecraft:campfire").requires.single().itemId)
    }

    @Test
    fun `answering the question settles both tags`() {
        val dag = PlanSelector.select(
            twoNamesGraph(),
            listOf(PlanTarget(torch, 4), PlanTarget(campfire, 1)),
            overrides = PlanOverrides(tagMember = mapOf("#minecraft:coals" to "minecraft:charcoal"))
        )

        assertTrue(dag.nodes.values.none { it.status == PlanNodeStatus.OPEN_TAG })
        assertEquals("minecraft:charcoal", dag.nodes.getValue("minecraft:torch").requires.single().itemId)
        assertEquals("minecraft:charcoal", dag.nodes.getValue("minecraft:campfire").requires.single().itemId)
    }

    @Test
    fun `an answer stored under the other name still settles it`() {
        // An override persisted before the fold — or against a graph ingested before extraction
        // stopped minting the synthetic name — is keyed by the id the user was shown then.
        val dag = PlanSelector.select(
            twoNamesGraph(),
            listOf(PlanTarget(torch, 4), PlanTarget(campfire, 1)),
            overrides = PlanOverrides(tagMember = mapOf("#mcorg:choice/charcoal_coal" to "minecraft:coal"))
        )

        assertTrue(dag.nodes.values.none { it.status == PlanNodeStatus.OPEN_TAG })
        assertEquals("minecraft:coal", dag.nodes.getValue("minecraft:torch").requires.single().itemId)
        assertEquals("minecraft:coal", dag.nodes.getValue("minecraft:campfire").requires.single().itemId)
    }

    @Test
    fun `the vanilla name represents the set, whatever the ids sort like`() {
        // "#mcorg:" sorts before "#minecraft:", so plain lexicographic order would pick the
        // synthetic id — which is a content hash for big sets and disappears on re-ingest.
        val identity = TagIdentity.of(twoNamesGraph())

        assertEquals("#minecraft:coals", identity.representative(choiceTag).id)
        assertEquals("#minecraft:coals", identity.representative(coalsTag).id)
        assertEquals(
            listOf("#minecraft:coals", "#mcorg:choice/charcoal_coal"),
            identity.equivalentIds(choiceTag),
        )
    }

    @Test
    fun `a subset is a different question and stays separate`() {
        // The sandstone choices overlap without being equal — {chiseled, cut, plain} against
        // {chiseled, plain}. Folding them would offer the narrower recipe an option it does not
        // take, so only equal sets fold.
        val chiseled = item("chiseled_sandstone")
        val cut = item("cut_sandstone")
        val plain = item("sandstone")
        val three = MinecraftTag("#mcorg:choice/three", "Three", listOf(chiseled, cut, plain))
        val two = MinecraftTag("#mcorg:choice/two", "Two", listOf(chiseled, plain))

        val identity = TagIdentity.of(
            Fixture().apply {
                loot(block, "blocks/sandstone.json", plain to 1)
                recipe("a.json", item("stairs") to 4, three to 6)
                recipe("b.json", item("slab") to 6, two to 3)
            }.build()
        )

        assertEquals("#mcorg:choice/three", identity.representative(three).id)
        assertEquals("#mcorg:choice/two", identity.representative(two).id)
    }

    @Test
    fun `a tag the graph does not contain is answered on its own terms`() {
        val identity = TagIdentity.of(twoNamesGraph())
        val unknown = MinecraftTag("#minecraft:wool", "Wool", listOf(item("white_wool")))

        assertEquals("#minecraft:wool", identity.representative(unknown).id)
        assertEquals(listOf("#minecraft:wool"), identity.equivalentIds(unknown))
    }
}
