package app.mcorg.data.minecraft.extract

import app.mcorg.domain.model.minecraft.MinecraftVersion
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

class ExtractionContextTest {

    private fun context(
        version: MinecraftVersion.Release = MinecraftVersion.Release(1, 21, 0),
        names: Map<String, String> = emptyMap(),
        tags: Map<String, List<String>> = emptyMap(),
        itemIds: Set<String> = emptySet(),
    ) = ExtractionContext(version, Path.of("/tmp/test"), names, tags, itemIds)

    @Test
    fun `nameOf falls back to the id when no name is known`() {
        assertEquals("minecraft:unknown_thing", context().nameOf("minecraft:unknown_thing"))
    }

    @Test
    fun `nameOf resolves a known name`() {
        val context = context(names = mapOf("minecraft:diamond" to "Diamond (Item)"))
        assertEquals("Diamond (Item)", context.nameOf("minecraft:diamond"))
    }

    @Test
    fun `nameOf resolves charged creeper drops to the mob head name`() {
        val context = context(names = mapOf("minecraft:creeper_head" to "Creeper Head (Item)"))
        assertEquals("Creeper Head (Item)", context.nameOf("minecraft:charged_creeper/creeper"))
    }

    @Test
    fun `nameOf resolves armor trim templates via the trim pattern name on 1_20`() {
        val context = context(
            version = MinecraftVersion.Release(1, 20, 0),
            names = mapOf("minecraft:armor_trim_coast" to "Coast Armor Trim"),
        )
        assertEquals("Coast Armor Trim", context.nameOf("minecraft:coast_armor_trim_smithing_template"))
    }

    @Test
    fun `nameOf uses the plain lang entry for armor trim templates after 1_20_1`() {
        val context = context(
            version = MinecraftVersion.Release(1, 21, 0),
            names = mapOf(
                "minecraft:armor_trim_coast" to "Coast Armor Trim",
                "minecraft:coast_armor_trim_smithing_template" to "Smithing Template (Item)",
            ),
        )
        assertEquals("Smithing Template (Item)", context.nameOf("minecraft:coast_armor_trim_smithing_template"))
    }

    @Test
    fun `contentOfTag resolves nested tag references recursively`() {
        val context = context(
            tags = mapOf(
                "#minecraft:logs" to listOf("#minecraft:oak_logs", "minecraft:crimson_stem"),
                "#minecraft:oak_logs" to listOf("minecraft:oak_log", "minecraft:stripped_oak_log"),
            )
        )
        assertEquals(
            listOf("minecraft:oak_log", "minecraft:stripped_oak_log", "minecraft:crimson_stem"),
            context.contentOfTag("#minecraft:logs")
        )
    }

    @Test
    fun `contentOfTag returns empty for unknown tags`() {
        assertEquals(emptyList(), context().contentOfTag("#minecraft:nope"))
    }

    @Test
    fun `tagDisplayName formats a tag id as title case words`() {
        assertEquals("Wooden Slabs", ExtractionContext.tagDisplayName("#minecraft:wooden_slabs"))
    }

    // --- registryIds (MCO-313) ---

    private fun langKeys(vararg keys: String) = keys.associateWith { "irrelevant" }

    @Test
    fun `registryIds reads dot-free item and block keys as ids`() {
        val ids = ExtractionContextFactory.registryIds(
            langKeys("item.minecraft.diamond", "block.minecraft.stone")
        )
        assertEquals(setOf("minecraft:diamond", "minecraft:stone"), ids)
    }

    @Test
    fun `registryIds ignores dotted auxiliary keys`() {
        val ids = ExtractionContextFactory.registryIds(
            langKeys("item.minecraft.splash_potion.effect.luck")
        )
        assertEquals(emptySet(), ids)
    }

    @Test
    fun `registryIds drops a legacy id when its replacement is present`() {
        val ids = ExtractionContextFactory.registryIds(
            langKeys("block.minecraft.chain", "block.minecraft.iron_chain")
        )
        assertEquals(setOf("minecraft:iron_chain"), ids)
    }

    /**
     * The rule has to be self-versioning: `chain` is the real item before 1.21.9, `grass`
     * before 1.20.3. Without the replacement present, the legacy id must survive.
     */
    @Test
    fun `registryIds keeps a legacy id on versions predating the rename`() {
        val ids = ExtractionContextFactory.registryIds(
            langKeys("block.minecraft.chain", "block.minecraft.grass", "item.minecraft.scute")
        )
        assertEquals(setOf("minecraft:chain", "minecraft:grass", "minecraft:scute"), ids)
    }

    @Test
    fun `registryIds drops family-label keys that were never items`() {
        val ids = ExtractionContextFactory.registryIds(
            langKeys(
                "item.minecraft.smithing_template",
                "item.minecraft.harness",
                "block.minecraft.set_spawn",
                "item.minecraft.white_harness",
            )
        )
        assertEquals(setOf("minecraft:white_harness"), ids)
    }
}
