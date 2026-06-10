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
import kotlin.test.assertNull
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

    private fun parsePools(jsonString: String): Result<*, Map<String, Double?>> = runBlocking {
        val pools = Json.parseToJsonElement(jsonString) as JsonArray
        poolParser.parsePools(pools, "test.json")
    }

    @Test
    fun `a single unweighted entry yields one per attempt`() {
        val json = """
        [
            {
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:diamond"}
                ]
            }
        ]
        """
        val yields = assertResultSuccess(parsePools(json))
        assertEquals(mapOf<String, Double?>("minecraft:diamond" to 1.0), yields)
    }

    @Test
    fun `two entries in one pool split the selection probability`() {
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
        val yields = assertResultSuccess(parsePools(json))
        assertEquals(0.5, yields["minecraft:diamond"])
        assertEquals(0.5, yields["minecraft:gold_ingot"])
    }

    @Test
    fun `weights skew the selection probability`() {
        val json = """
        [
            {
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:stick", "weight": 3},
                    {"type": "minecraft:item", "name": "minecraft:glowstone_dust", "weight": 1}
                ]
            }
        ]
        """
        val yields = assertResultSuccess(parsePools(json))
        assertEquals(0.75, yields["minecraft:stick"])
        assertEquals(0.25, yields["minecraft:glowstone_dust"])
    }

    @Test
    fun `rolls multiply the yield`() {
        val json = """
        [
            {
                "rolls": {"min": 1, "max": 3},
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:stick", "weight": 1},
                    {"type": "minecraft:item", "name": "minecraft:sugar", "weight": 1}
                ]
            }
        ]
        """
        val yields = assertResultSuccess(parsePools(json))
        // 2 average rolls * 0.5 share * 1 count
        assertEquals(1.0, yields["minecraft:stick"])
    }

    @Test
    fun `set_count scales the yield`() {
        val json = """
        [
            {
                "rolls": 1,
                "entries": [
                    {
                        "type": "minecraft:item",
                        "name": "minecraft:redstone",
                        "functions": [{"function": "minecraft:set_count", "count": {"min": 4, "max": 5}}]
                    }
                ]
            }
        ]
        """
        val yields = assertResultSuccess(parsePools(json))
        assertEquals(4.5, yields["minecraft:redstone"])
    }

    @Test
    fun `multiple pools sum their yields`() {
        val json = """
        [
            {
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:diamond"}
                ]
            },
            {
                "rolls": 2,
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:diamond"}
                ]
            }
        ]
        """
        val yields = assertResultSuccess(parsePools(json))
        assertEquals(3.0, yields["minecraft:diamond"])
    }

    @Test
    fun `aggregates distinct items across pools`() {
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
        val yields = assertResultSuccess(parsePools(json))
        assertEquals(setOf("minecraft:diamond", "minecraft:emerald"), yields.keys)
    }

    @Test
    fun `an unknown contribution keeps the known signal from another pool`() {
        val json = """
        [
            {
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:diamond"}
                ]
            },
            {
                "entries": [
                    {
                        "type": "minecraft:item",
                        "name": "minecraft:diamond",
                        "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:binomial", "n": 2, "p": 0.5}}]
                    }
                ]
            }
        ]
        """
        val yields = assertResultSuccess(parsePools(json))
        assertEquals(1.0, yields["minecraft:diamond"])
    }

    @Test
    fun `an item only reachable through conditions has unknown yield`() {
        val json = """
        [
            {
                "entries": [
                    {
                        "type": "minecraft:alternatives",
                        "children": [
                            {"type": "minecraft:item", "name": "minecraft:cobblestone"},
                            {"type": "minecraft:item", "name": "minecraft:stone"}
                        ]
                    }
                ]
            }
        ]
        """
        val yields = assertResultSuccess(parsePools(json))
        assertNull(yields["minecraft:cobblestone"])
        assertEquals(1.0, yields["minecraft:stone"])
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
        val yields = assertResultSuccess(parsePools(json))
        assertTrue(yields.isEmpty())
    }

    @Test
    fun `an empty entry's weight dilutes its siblings`() {
        val json = """
        [
            {
                "entries": [
                    {"type": "minecraft:item", "name": "minecraft:saddle", "weight": 1},
                    {"type": "minecraft:empty", "weight": 3}
                ]
            }
        ]
        """
        val yields = assertResultSuccess(parsePools(json))
        assertEquals(0.25, yields["minecraft:saddle"])
    }
}
