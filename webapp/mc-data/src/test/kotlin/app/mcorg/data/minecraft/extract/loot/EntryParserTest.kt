package app.mcorg.data.minecraft.extract.loot

import app.mcorg.data.minecraft.TestUtils.assertResultFailure
import app.mcorg.data.minecraft.TestUtils.assertResultSuccess
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EntryParserTest {

    private lateinit var entryParser: EntryParser
    private lateinit var poolParser: PoolParser

    @BeforeEach
    fun setup() {
        entryParser = EntryParser(
            path = Path.of("/tmp/test"),
            version = MinecraftVersion.Release(1, 21, 0)
        )
        poolParser = PoolParser()
        poolParser.entryParser = entryParser
        entryParser.poolParser = poolParser
    }

    private fun parseEntry(jsonString: String): Result<*, Set<String>> = runBlocking {
        entryParser.parseEntry(Json.parseToJsonElement(jsonString), "test.json")
    }

    @Test
    fun `parses empty entry type`() {
        val result = parseEntry("""{"type": "minecraft:empty"}""")
        val items = assertResultSuccess(result)
        assertTrue(items.isEmpty())
    }

    @Test
    fun `parses item entry type`() {
        val result = parseEntry("""{"type": "minecraft:item", "name": "minecraft:diamond"}""")
        val items = assertResultSuccess(result)
        assertEquals(setOf("minecraft:diamond"), items)
    }

    @Test
    fun `parses tag entry with tag field`() {
        val result = parseEntry("""{"type": "minecraft:tag", "tag": "minecraft:logs"}""")
        val items = assertResultSuccess(result)
        assertEquals(setOf("#minecraft:logs"), items)
    }

    @Test
    fun `parses tag entry with name fallback`() {
        val result = parseEntry("""{"type": "minecraft:tag", "name": "minecraft:planks"}""")
        val items = assertResultSuccess(result)
        assertEquals(setOf("#minecraft:planks"), items)
    }

    @Test
    fun `parses dynamic entry with contents`() {
        val result = parseEntry("""{"type": "minecraft:dynamic", "name": "minecraft:contents"}""")
        val items = assertResultSuccess(result)
        assertTrue(items.isEmpty())
    }

    @Test
    fun `parses dynamic entry with sherds`() {
        val result = parseEntry("""{"type": "minecraft:dynamic", "name": "minecraft:sherds"}""")
        val items = assertResultSuccess(result)
        assertEquals(setOf("#minecraft:decorated_pot_sherds"), items)
    }

    @Test
    fun `fails on unknown dynamic entry name`() {
        val result = parseEntry("""{"type": "minecraft:dynamic", "name": "minecraft:unknown_type"}""")
        assertResultFailure(result)
    }

    @Test
    fun `parses alternatives entry recursively`() {
        val json = """
        {
            "type": "minecraft:alternatives",
            "children": [
                {"type": "minecraft:item", "name": "minecraft:diamond"},
                {"type": "minecraft:item", "name": "minecraft:emerald"}
            ]
        }
        """
        val items = assertResultSuccess(parseEntry(json))
        assertEquals(setOf("minecraft:diamond", "minecraft:emerald"), items)
    }

    @Test
    fun `parses nested alternatives`() {
        val json = """
        {
            "type": "minecraft:alternatives",
            "children": [
                {
                    "type": "minecraft:alternatives",
                    "children": [
                        {"type": "minecraft:item", "name": "minecraft:diamond"}
                    ]
                },
                {"type": "minecraft:empty"}
            ]
        }
        """
        val items = assertResultSuccess(parseEntry(json))
        assertEquals(setOf("minecraft:diamond"), items)
    }

    @Test
    fun `fails on unknown entry type`() {
        val result = parseEntry("""{"type": "minecraft:unknown_type"}""")
        assertResultFailure(result)
    }

    @Test
    fun `fails when type field is missing`() {
        val result = parseEntry("""{"name": "minecraft:diamond"}""")
        assertResultFailure(result)
    }

    @Test
    fun `parses multiple entries`() {
        val entries = Json.parseToJsonElement("""
            [
                {"type": "minecraft:item", "name": "minecraft:diamond"},
                {"type": "minecraft:item", "name": "minecraft:gold_ingot"},
                {"type": "minecraft:empty"}
            ]
        """)
        val result = runBlocking {
            entryParser.parseEntries(
                entries as kotlinx.serialization.json.JsonArray,
                "test.json"
            )
        }
        val items = assertResultSuccess(result)
        assertEquals(setOf("minecraft:diamond", "minecraft:gold_ingot"), items)
    }
}
