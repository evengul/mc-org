package app.mcorg.data.minecraft.extract.loot

import app.mcorg.data.minecraft.TestUtils.assertResultSuccess
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoolParserTest {

    private lateinit var poolParser: PoolParser

    @BeforeEach
    fun setup() {
        poolParser = PoolParser()
        val entryParser = EntryParser(
            path = Path.of("/tmp/test"),
            version = MinecraftVersion.Release(1, 21, 0)
        )
        poolParser.entryParser = entryParser
        entryParser.poolParser = poolParser
    }

    private fun parsePools(jsonString: String): Result<*, Set<String>> = runBlocking {
        val pools = Json.parseToJsonElement(jsonString) as JsonArray
        poolParser.parsePool(pools, "test.json")
    }

    @Test
    fun `parses single pool with single entry`() {
        val json = """
        [
            {
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:diamond"}
                ]
            }
        ]
        """
        val items = assertResultSuccess(parsePools(json))
        assertEquals(setOf("minecraft:diamond"), items)
    }

    @Test
    fun `parses single pool with multiple entries`() {
        val json = """
        [
            {
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:diamond"},
                    {"type": "minecraft:item", "name": "minecraft:gold_ingot"}
                ]
            }
        ]
        """
        val items = assertResultSuccess(parsePools(json))
        assertEquals(setOf("minecraft:diamond", "minecraft:gold_ingot"), items)
    }

    @Test
    fun `parses multiple pools and aggregates items`() {
        val json = """
        [
            {
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:diamond"}
                ]
            },
            {
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:emerald"}
                ]
            }
        ]
        """
        val items = assertResultSuccess(parsePools(json))
        assertEquals(setOf("minecraft:diamond", "minecraft:emerald"), items)
    }

    @Test
    fun `deduplicates items across pools`() {
        val json = """
        [
            {
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:diamond"}
                ]
            },
            {
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:diamond"}
                ]
            }
        ]
        """
        val items = assertResultSuccess(parsePools(json))
        assertEquals(1, items.size)
    }

    @Test
    fun `handles empty entries`() {
        val json = """
        [
            {
                "entries": [
                    {"type": "minecraft:empty"}
                ]
            }
        ]
        """
        val items = assertResultSuccess(parsePools(json))
        assertTrue(items.isEmpty())
    }
}
