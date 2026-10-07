package app.mcorg.engine.renewability

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.model.resources.ResourceSource.SourceType
import app.mcorg.domain.services.ItemSourceGraphBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules of [Renewability] on graphs small enough to read. The real data is pinned by
 * `RenewabilitySnapshotTest`; these say what each rule is for, so a snapshot that moves can be
 * told apart from a rule that broke.
 */
class RenewabilityTest {

    private fun id(name: String) = if (name.startsWith("#") || name.contains(':')) name else "minecraft:$name"
    private fun item(name: String): MinecraftId = Item(id(name), name)
    private fun one(name: String) = item(name) to ResourceQuantity.ItemQuantity(1)

    private fun source(
        type: SourceType,
        filename: String,
        produces: List<String>,
        consumes: List<String> = emptyList(),
        consumesTag: Pair<String, List<String>>? = null,
    ) = ResourceSource(
        type = type,
        filename = filename,
        requiredItems = consumes.map(::one) +
            listOfNotNull(consumesTag?.let { (tag, members) ->
                MinecraftTag(tag, tag, members.map { Item(id(it), it) }) to ResourceQuantity.ItemQuantity(1)
            }),
        producedItems = produces.map(::one),
    )

    private fun mobDrop(mob: String, vararg drops: String) =
        source(SourceType.LootTypes.ENTITY, "entities/$mob.json", drops.toList())

    private fun blockLoot(block: String, vararg drops: String) =
        source(SourceType.LootTypes.BLOCK, "blocks/$block.json", drops.toList().ifEmpty { listOf(block) })

    private fun recipe(output: String, vararg inputs: String) =
        source(SourceType.RecipeTypes.CRAFTING_SHAPELESS, "$output.json", listOf(output), inputs.toList())

    private fun renewability(
        sources: List<ResourceSource>,
        extraRegistry: List<String> = emptyList(),
        tags: Map<String, Set<String>> = emptyMap(),
        donor: List<ResourceSource>? = null,
    ): Renewability {
        val registry = (sources.flatMap { s -> (s.producedItems + s.requiredItems).map { it.first } }
            .filterNot { it is MinecraftTag }.map { it.id } + extraRegistry.map(::id)).toSet()
        return Renewability.of(
            ItemSourceGraphBuilder.buildFromResourceSources(sources),
            registry,
            tags,
            donor?.let { Renewability.TradeDonor("26.x", ItemSourceGraphBuilder.buildFromResourceSources(it)) },
        )
    }

    private fun Renewability.yes(name: String) = assertTrue(isRenewable(id(name)), "$name should be renewable")
    private fun Renewability.no(name: String) = assertFalse(isRenewable(id(name)), "$name should not be renewable")

    // ── sources that prove on their own ─────────────────────────────────────

    @Test
    fun `a mob drop, fishing, shearing and bartering prove what they make`() {
        val r = renewability(
            listOf(
                mobDrop("zombie", "rotten_flesh"),
                source(SourceType.LootTypes.FISHING, "gameplay/fishing.json", listOf("cod")),
                source(SourceType.LootTypes.SHEARING, "shearing/sheep.json", listOf("white_wool")),
                source(SourceType.LootTypes.BARTER, "gameplay/piglin_bartering.json", listOf("blackstone")),
            )
        )
        r.yes("rotten_flesh"); r.yes("cod"); r.yes("white_wool"); r.yes("blackstone")
    }

    @Test
    fun `a villager trade proves its goods once its price is renewable`() {
        val trade = source(SourceType.TradeTypes.TOOLSMITH, "toolsmith/1/emerald_bell.json", listOf("bell"), listOf("emerald"))
        renewability(listOf(trade, blockLoot("emerald_ore", "emerald"))).no("bell")
        renewability(listOf(trade, blockLoot("emerald_ore", "emerald"), mobDrop("vindicator", "emerald"))).yes("bell")
    }

    // ── the never-list ──────────────────────────────────────────────────────

    @Test
    fun `chest loot, archaeology, the wandering trader, raid gifts and elder guardians prove nothing`() {
        val r = renewability(
            listOf(
                source(SourceType.LootTypes.CHEST, "chests/ancient_city.json", listOf("echo_shard")),
                source(SourceType.LootTypes.ARCHAEOLOGY, "archaeology/desert_well.json", listOf("arms_up_pottery_sherd")),
                source(SourceType.TradeTypes.WANDERING_TRADER, "wandering_trader/emerald_tuff.json", listOf("tuff")),
                source(SourceType.LootTypes.GIFT, "gameplay/hero_of_the_village/mason_gift.json", listOf("clay_ball")),
                mobDrop("elder_guardian", "wet_sponge"),
            )
        )
        r.no("echo_shard"); r.no("arms_up_pottery_sherd"); r.no("tuff"); r.no("clay_ball"); r.no("wet_sponge")
        val why = r.explain().associateBy { it.itemId }
        assertEquals("elder guardians do not respawn", why.getValue("wet_sponge").blocked.single().why)
        assertTrue(why.getValue("tuff").blocked.single().never)
    }

    // ── recipes ─────────────────────────────────────────────────────────────

    @Test
    fun `a recipe proves its output only when every input is renewable`() {
        val sources = listOf(blockLoot("tuff"), mobDrop("skeleton", "bone"), recipe("tuff_bricks", "tuff", "bone"))
        renewability(sources).no("tuff_bricks")
        renewability(sources + mobDrop("magic", "tuff")).yes("tuff_bricks")
    }

    @Test
    fun `a tag input needs any one renewable member`() {
        val planks = "#minecraft:planks" to listOf("oak_planks", "warped_planks")
        val stick = source(SourceType.RecipeTypes.CRAFTING_SHAPED, "stick.json", listOf("stick"), consumesTag = planks)
        renewability(listOf(stick, blockLoot("oak_planks"), blockLoot("warped_planks"))).no("stick")
        renewability(listOf(stick, blockLoot("oak_planks"), mobDrop("magic", "warped_planks"))).yes("stick")
    }

    @Test
    fun `a loop proves nothing`() {
        val r = renewability(listOf(recipe("alpha", "beta"), recipe("beta", "alpha")))
        r.no("alpha"); r.no("beta")
    }

    // ── blocks ──────────────────────────────────────────────────────────────

    @Test
    fun `breaking a block proves its drops only when the block is renewable`() {
        // Raw iron comes off the ore, the ore is mined and never made: not renewable. The ingot
        // a golem drops is, which is why the farm-scale rule looks at what raw iron feeds.
        val r = renewability(
            listOf(blockLoot("iron_ore", "raw_iron"), mobDrop("iron_golem", "iron_ingot"), recipe("iron_ingot", "raw_iron"))
        )
        r.no("raw_iron"); r.no("iron_ore"); r.yes("iron_ingot")
    }

    @Test
    fun `a block nothing produces is judged by any one renewable drop`() {
        // `potatoes` is placed by planting a potato and is never an item, so a zombie's potato
        // makes the crop renewable, and with it everything the crop drops.
        val crop = blockLoot("potatoes", "potato", "poisonous_potato")
        renewability(listOf(crop)).no("poisonous_potato")
        renewability(listOf(crop, mobDrop("zombie", "potato"))).yes("poisonous_potato")
    }

    @Test
    fun `a potted plant needs the plant as well as the pot`() {
        val r = renewability(listOf(blockLoot("potted_wither_rose", "flower_pot", "wither_rose"), mobDrop("magic", "flower_pot")))
        r.yes("flower_pot")
        r.no("wither_rose")
    }

    @Test
    fun `interacting with a block needs the block to be renewable`() {
        val harvest = source(SourceType.LootTypes.BLOCK_INTERACT, "harvest/bee_nest.json", listOf("honeycomb"))
        renewability(listOf(harvest, blockLoot("bee_nest"))).no("honeycomb")
        renewability(listOf(harvest, blockLoot("bee_nest"), mobDrop("magic", "bee_nest"))).yes("honeycomb")
    }

    @Test
    fun `the cave vine harvest acts on cave_vines, which the file does not spell out`() {
        // `harvest/cave_vine` names no real block. Read literally it would be judged as an unknown
        // block nothing makes, and prove nothing; mapped to `cave_vines`, it inherits the vines'
        // verdict, which their glow berries (grown by a mechanic) settle.
        val harvest = source(SourceType.LootTypes.BLOCK_INTERACT, "harvest/cave_vine.json", listOf("vine_fruit"))
        val r = renewability(listOf(harvest, blockLoot("cave_vines", "glow_berries")))
        r.yes("glow_berries")
        r.yes("vine_fruit")
    }

    // ── mechanics ───────────────────────────────────────────────────────────

    @Test
    fun `a conversion mechanic needs its input`() {
        // Grass spreads onto dirt; it does not make grass from nothing.
        val grass = listOf("grass_block")
        val noDirt = renewability(listOf(blockLoot("grass_block", "dirt"), blockLoot("dirt")), extraRegistry = grass)
        noDirt.no("grass_block"); noDirt.no("dirt")
        val dirt = renewability(listOf(blockLoot("grass_block", "dirt"), blockLoot("dirt"), mobDrop("magic", "dirt")), extraRegistry = grass)
        dirt.yes("grass_block")
    }

    @Test
    fun `tree growth reads the logs and leaves tags`() {
        val r = renewability(
            listOf(blockLoot("poplar_log"), blockLoot("poplar_leaves")),
            tags = mapOf("#minecraft:logs" to setOf("minecraft:poplar_log"), "#minecraft:leaves" to setOf("minecraft:poplar_leaves")),
        )
        r.yes("poplar_log"); r.yes("poplar_leaves")
    }

    @Test
    fun `a mechanic only makes what the version has`() {
        val mechanics = RenewabilityMechanics.forVersion(setOf("minecraft:moss_block", "minecraft:bone_meal", "minecraft:stone"), emptyMap())
        val made = mechanics.flatMap { it.produces }.toSet()
        assertTrue("minecraft:moss_block" in made)
        assertFalse("minecraft:pale_moss_block" in made, "a version without pale moss must not gain it")
        assertFalse(mechanics.any { "minecraft:copper_golem_statue" in it.produces }, "needs copper_block, which this version lacks")
    }

    @Test
    fun `copper ages by name, and bare copper ages from the copper block`() {
        val registry = setOf("minecraft:copper_block", "minecraft:exposed_copper", "minecraft:weathered_copper", "minecraft:exposed_cut_copper")
        val ageing = RenewabilityMechanics.forVersion(registry, emptyMap())
            .filter { it.family == "copper oxidation" }
            .associate { it.produces.single() to it.requires.single() }
        assertEquals("minecraft:copper_block", ageing["minecraft:exposed_copper"])
        assertEquals("minecraft:exposed_copper", ageing["minecraft:weathered_copper"])
        assertNull(ageing["minecraft:exposed_cut_copper"], "cut_copper is not in this registry, so nothing ages into it")
    }

    // ── borrowing trades ────────────────────────────────────────────────────

    @Test
    fun `a version without trades borrows villager trades whose items it has`() {
        val emeralds = mobDrop("vindicator", "emerald")
        val donor = listOf(
            source(SourceType.TradeTypes.FARMER, "farmer/2/emerald_apple.json", listOf("apple"), listOf("emerald")),
            source(SourceType.TradeTypes.MASON, "mason/4/emerald_pale_moss.json", listOf("pale_moss_block"), listOf("emerald")),
            source(SourceType.TradeTypes.WANDERING_TRADER, "wandering_trader/emerald_tuff.json", listOf("tuff"), listOf("emerald")),
        )
        val r = renewability(listOf(emeralds, blockLoot("tuff")), extraRegistry = listOf("apple"), donor = donor)
        r.yes("apple")
        r.no("tuff")
        assertFalse(r.isRenewable("minecraft:pale_moss_block"), "not in this version's registry, so not borrowed")
        assertTrue(Renewability.hasVillagerTrades(ItemSourceGraphBuilder.buildFromResourceSources(donor)))
    }

    // ── not a material ──────────────────────────────────────────────────────

    @Test
    fun `the nether portal is never listed, though its source makes it read as renewable`() {
        val r = renewability(listOf(source(SourceType.MechanicTypes.IN_WORLD_TRANSFORM, "synthetic/nether_portal.json", listOf("nether_portal"))))
        assertTrue(r.isRenewable("minecraft:nether_portal"))
        assertTrue(r.explain().none { it.itemId == "nether_portal" })
        assertTrue("minecraft:nether_portal" in Renewability.NOT_MATERIALS)
    }
}
