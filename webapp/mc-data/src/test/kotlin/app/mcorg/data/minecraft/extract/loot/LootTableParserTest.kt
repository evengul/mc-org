package app.mcorg.data.minecraft.extract.loot

import app.mcorg.data.minecraft.TestUtils.assertResultFailure
import app.mcorg.data.minecraft.TestUtils.assertResultSuccess
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LootTableParserTest {

    private lateinit var parser: LootTableParser

    @BeforeEach
    fun setup() {
        val poolParser = PoolParser()
        val entryParser = EntryParser(
            path = Path.of("/tmp/test"),
            version = MinecraftVersion.Release(1, 21, 0)
        )
        parser = LootTableParser(poolParser, entryParser)
    }

    private fun parse(jsonString: String): Result<*, ResourceSource> = runBlocking {
        parser.parse(Json.parseToJsonElement(jsonString), "test.json")
    }

    @Test
    fun `parses loot table with items`() {
        val json = """
        {
            "type": "minecraft:block",
            "pools": [
                {
                    "entries": [
                        {"type": "minecraft:item", "name": "minecraft:diamond"}
                    ]
                }
            ]
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceSource.SourceType.LootTypes.BLOCK, source.type)
        assertEquals(1, source.producedItems.size)
        assertIs<Item>(source.producedItems[0].first)
        assertEquals("minecraft:diamond", source.producedItems[0].first.id)
        assertEquals(ResourceQuantity.Unknown, source.producedItems[0].second)
    }

    @Test
    fun `handles no-pools loot table`() {
        val json = """
        {
            "type": "minecraft:block"
        }
        """
        val source = assertResultSuccess(parse(json))
        assertTrue(source.producedItems.isEmpty())
    }

    @Test
    fun `parses tag entries as MinecraftTag`() {
        val json = """
        {
            "type": "minecraft:chest",
            "pools": [
                {
                    "entries": [
                        {"type": "minecraft:tag", "tag": "minecraft:logs"}
                    ]
                }
            ]
        }
        """
        val source = assertResultSuccess(parse(json))
        assertIs<MinecraftTag>(source.producedItems[0].first)
        assertEquals("#minecraft:logs", source.producedItems[0].first.id)
    }

    @Test
    fun `all produced items have Unknown quantity`() {
        val json = """
        {
            "type": "minecraft:entity",
            "pools": [
                {
                    "entries": [
                        {"type": "minecraft:item", "name": "minecraft:bone"},
                        {"type": "minecraft:item", "name": "minecraft:arrow"}
                    ]
                }
            ]
        }
        """
        val source = assertResultSuccess(parse(json))
        source.producedItems.forEach { (_, quantity) ->
            assertEquals(ResourceQuantity.Unknown, quantity)
        }
    }

    @Test
    fun `routes different loot table types correctly`() {
        val types = listOf(
            "minecraft:block" to ResourceSource.SourceType.LootTypes.BLOCK,
            "minecraft:entity" to ResourceSource.SourceType.LootTypes.ENTITY,
            "minecraft:chest" to ResourceSource.SourceType.LootTypes.CHEST,
            "minecraft:fishing" to ResourceSource.SourceType.LootTypes.FISHING,
        )

        for ((typeString, expectedType) in types) {
            val json = """{"type": "$typeString", "pools": [{"entries": [{"type": "minecraft:item", "name": "minecraft:stone"}]}]}"""
            val source = assertResultSuccess(parse(json))
            assertEquals(expectedType, source.type, "Type mismatch for $typeString")
        }
    }

    @Test
    fun `unknown loot table type maps to UNKNOWN`() {
        // SourceType.of returns UNKNOWN for unrecognized types rather than null
        val json = """
        {
            "type": "minecraft:completely_unknown",
            "pools": [
                {"entries": [{"type": "minecraft:item", "name": "minecraft:stone"}]}
            ]
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceSource.SourceType.UNKNOWN, source.type)
    }
}
