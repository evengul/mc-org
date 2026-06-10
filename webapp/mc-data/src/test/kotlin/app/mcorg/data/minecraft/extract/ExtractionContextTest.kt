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
    ) = ExtractionContext(version, Path.of("/tmp/test"), names, tags)

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
}
