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

    /**
     * MCO-488. `tags/item/enchantable/foot_armor.json`'s only value is `#minecraft:foot_armor`;
     * when both files were keyed by base filename the entry named itself and this recursed until
     * the stack went. The test would not even fail — it would kill the JVM's thread.
     */
    @Test
    fun `contentOfTag survives a self-referential tag`() {
        val context = context(tags = mapOf("#minecraft:foot_armor" to listOf("#minecraft:foot_armor")))

        assertEquals(emptyList(), context.contentOfTag("#minecraft:foot_armor"))
    }

    @Test
    fun `contentOfTag keeps the real members of a tag that also references itself`() {
        val context = context(
            tags = mapOf("#minecraft:coals" to listOf("minecraft:coal", "#minecraft:coals", "minecraft:charcoal"))
        )

        assertEquals(listOf("minecraft:coal", "minecraft:charcoal"), context.contentOfTag("#minecraft:coals"))
    }

    @Test
    fun `contentOfTag breaks a cycle between two tags without losing either's items`() {
        val context = context(
            tags = mapOf(
                "#minecraft:a" to listOf("minecraft:one", "#minecraft:b"),
                "#minecraft:b" to listOf("minecraft:two", "#minecraft:a"),
            )
        )

        assertEquals(listOf("minecraft:one", "minecraft:two"), context.contentOfTag("#minecraft:a"))
    }

    /** Only cycles break. A tag reached twice down two different branches still expands twice. */
    @Test
    fun `contentOfTag still expands a tag referenced by two siblings`() {
        val context = context(
            tags = mapOf(
                "#minecraft:logs" to listOf("#minecraft:oak_logs", "#minecraft:burnable_logs"),
                "#minecraft:burnable_logs" to listOf("#minecraft:oak_logs"),
                "#minecraft:oak_logs" to listOf("minecraft:oak_log"),
            )
        )

        assertEquals(listOf("minecraft:oak_log", "minecraft:oak_log"), context.contentOfTag("#minecraft:logs"))
    }

    /** Two vanilla tags with the same base filename are two tags, and keep their own contents. */
    @Test
    fun `filenameToTagId keys a nested tag by its path, not its base filename`() {
        assertEquals(
            "#minecraft:foot_armor",
            ExtractionContextFactory.filenameToTagId("foot_armor.json"),
        )
        assertEquals(
            "#minecraft:enchantable/foot_armor",
            ExtractionContextFactory.filenameToTagId("enchantable/foot_armor.json"),
        )
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
