package app.mcorg.data.minecraft.extract.loot

import app.mcorg.data.minecraft.TestUtils.assertResultFailure
import app.mcorg.data.minecraft.TestUtils.assertResultSuccess
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntryParserTest {

    private lateinit var entryParser: LootTableParser

    @BeforeEach
    fun setup() {
        entryParser = LootTableParser(
            path = Path.of("/tmp/test"),
            version = MinecraftVersion.Release(1, 21, 0)
        )
    }

    private fun parseEntry(jsonString: String): Result<*, LootEntry> = runBlocking {
        entryParser.parseEntry(Json.parseToJsonElement(jsonString), "test.json")
    }

    private fun LootEntry.itemIds(): Set<String> = drops.map { it.itemId }.toSet()

    @Test
    fun `parses empty entry type`() {
        val entry = assertResultSuccess(parseEntry("""{"type": "minecraft:empty"}"""))
        assertTrue(entry.drops.isEmpty())
        assertEquals(1.0, entry.weight)
    }

    @Test
    fun `parses item entry type`() {
        val entry = assertResultSuccess(parseEntry("""{"type": "minecraft:item", "name": "minecraft:diamond"}"""))
        assertEquals(setOf("minecraft:diamond"), entry.itemIds())
        assertEquals(1.0, entry.drops.single().countPerSelection)
    }

    @Test
    fun `reads entry weight`() {
        val entry = assertResultSuccess(
            parseEntry("""{"type": "minecraft:item", "name": "minecraft:diamond", "weight": 5}""")
        )
        assertEquals(5.0, entry.weight)
    }

    @Test
    fun `set_count with a constant becomes the drop count`() {
        val json = """
        {
            "type": "minecraft:item",
            "name": "minecraft:stick",
            "functions": [{"function": "minecraft:set_count", "count": 3}]
        }
        """
        val entry = assertResultSuccess(parseEntry(json))
        assertEquals(3.0, entry.drops.single().countPerSelection)
    }

    @Test
    fun `set_count with a uniform range averages it`() {
        val json = """
        {
            "type": "minecraft:item",
            "name": "minecraft:redstone",
            "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]
        }
        """
        val entry = assertResultSuccess(parseEntry(json))
        assertEquals(2.0, entry.drops.single().countPerSelection)
    }

    @Test
    fun `set_count with add accumulates on the running count`() {
        val json = """
        {
            "type": "minecraft:item",
            "name": "minecraft:bone",
            "functions": [
                {"function": "minecraft:set_count", "count": 2},
                {"function": "minecraft:set_count", "count": 1, "add": true}
            ]
        }
        """
        val entry = assertResultSuccess(parseEntry(json))
        assertEquals(3.0, entry.drops.single().countPerSelection)
    }

    @Test
    fun `an unrecognized count provider yields an unknown count`() {
        val json = """
        {
            "type": "minecraft:item",
            "name": "minecraft:emerald",
            "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:binomial", "n": 3, "p": 0.5}}]
        }
        """
        val entry = assertResultSuccess(parseEntry(json))
        assertNull(entry.drops.single().countPerSelection)
    }

    @Test
    fun `parses tag entry with tag field`() {
        val entry = assertResultSuccess(parseEntry("""{"type": "minecraft:tag", "tag": "minecraft:logs"}"""))
        assertEquals(setOf("#minecraft:logs"), entry.itemIds())
    }

    @Test
    fun `parses tag entry with name fallback`() {
        val entry = assertResultSuccess(parseEntry("""{"type": "minecraft:tag", "name": "minecraft:planks"}"""))
        assertEquals(setOf("#minecraft:planks"), entry.itemIds())
    }

    @Test
    fun `parses dynamic entry with contents`() {
        val entry = assertResultSuccess(parseEntry("""{"type": "minecraft:dynamic", "name": "minecraft:contents"}"""))
        assertTrue(entry.drops.isEmpty())
    }

    @Test
    fun `parses dynamic entry with sherds`() {
        val entry = assertResultSuccess(parseEntry("""{"type": "minecraft:dynamic", "name": "minecraft:sherds"}"""))
        assertEquals(setOf("#minecraft:decorated_pot_sherds"), entry.itemIds())
        assertNull(entry.drops.single().countPerSelection)
    }

    @Test
    fun `fails on unknown dynamic entry name`() {
        assertResultFailure(parseEntry("""{"type": "minecraft:dynamic", "name": "minecraft:unknown_type"}"""))
    }

    @Test
    fun `alternatives keep all items but only the fallback child carries yield`() {
        val json = """
        {
            "type": "minecraft:alternatives",
            "children": [
                {"type": "minecraft:item", "name": "minecraft:diamond_ore"},
                {"type": "minecraft:item", "name": "minecraft:diamond"}
            ]
        }
        """
        val entry = assertResultSuccess(parseEntry(json))
        assertEquals(setOf("minecraft:diamond_ore", "minecraft:diamond"), entry.itemIds())
        assertNull(entry.drops.first { it.itemId == "minecraft:diamond_ore" }.countPerSelection)
        assertEquals(1.0, entry.drops.first { it.itemId == "minecraft:diamond" }.countPerSelection)
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
        val entry = assertResultSuccess(parseEntry(json))
        assertEquals(setOf("minecraft:diamond"), entry.itemIds())
    }

    @Test
    fun `fails on unknown entry type`() {
        assertResultFailure(parseEntry("""{"type": "minecraft:unknown_type"}"""))
    }

    @Test
    fun `fails when type field is missing`() {
        assertResultFailure(parseEntry("""{"name": "minecraft:diamond"}"""))
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
        val parsed = assertResultSuccess(result)
        assertEquals(3, parsed.size)
        assertEquals(
            setOf("minecraft:diamond", "minecraft:gold_ingot"),
            parsed.flatMap { it.drops }.map { it.itemId }.toSet()
        )
    }
}
