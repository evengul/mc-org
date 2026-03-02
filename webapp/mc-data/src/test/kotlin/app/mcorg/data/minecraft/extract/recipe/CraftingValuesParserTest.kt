package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.data.minecraft.TestUtils.assertResultFailure
import app.mcorg.data.minecraft.TestUtils.assertResultSuccess
import app.mcorg.pipeline.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CraftingValuesParserTest {

    private fun parse(jsonString: String): Result<*, Pair<String, List<List<String>>>> = runBlocking {
        CraftingValuesParser.parse(Json.parseToJsonElement(jsonString), "test.json")
    }

    @Test
    fun `parses ingredients as primitives`() {
        val json = """
        {
            "result": "minecraft:oak_planks",
            "ingredients": ["minecraft:oak_log"]
        }
        """
        val (resultId, ingredients) = assertResultSuccess(parse(json))
        assertEquals("minecraft:oak_planks", resultId)
        assertEquals(1, ingredients.size)
        assertEquals(listOf("minecraft:oak_log"), ingredients[0])
    }

    @Test
    fun `parses multiple ingredients`() {
        val json = """
        {
            "result": "minecraft:stick",
            "ingredients": ["minecraft:oak_planks", "minecraft:oak_planks"]
        }
        """
        val (resultId, ingredients) = assertResultSuccess(parse(json))
        assertEquals("minecraft:stick", resultId)
        assertEquals(2, ingredients.size)
    }

    @Test
    fun `parses ingredients as objects with item field`() {
        val json = """
        {
            "result": "minecraft:torch",
            "ingredients": [
                {"item": "minecraft:coal"},
                {"item": "minecraft:stick"}
            ]
        }
        """
        val (_, ingredients) = assertResultSuccess(parse(json))
        assertEquals(2, ingredients.size)
        assertEquals(listOf("minecraft:coal"), ingredients[0])
        assertEquals(listOf("minecraft:stick"), ingredients[1])
    }

    @Test
    fun `parses ingredients as objects with tag field`() {
        val json = """
        {
            "result": "minecraft:chest",
            "ingredients": [
                {"tag": "minecraft:planks"}
            ]
        }
        """
        val (_, ingredients) = assertResultSuccess(parse(json))
        assertEquals(1, ingredients.size)
        assertEquals(listOf("#minecraft:planks"), ingredients[0])
    }

    @Test
    fun `parses ingredient arrays as alternatives`() {
        val json = """
        {
            "result": "minecraft:wooden_sword",
            "ingredients": [
                ["minecraft:oak_planks", "minecraft:birch_planks"],
                "minecraft:stick"
            ]
        }
        """
        val (_, ingredients) = assertResultSuccess(parse(json))
        assertEquals(2, ingredients.size)
        assertTrue(ingredients[0].contains("minecraft:oak_planks"))
        assertTrue(ingredients[0].contains("minecraft:birch_planks"))
        assertEquals(listOf("minecraft:stick"), ingredients[1])
    }

    @Test
    fun `falls back to key when ingredients missing`() {
        val json = """
        {
            "result": "minecraft:crafting_table",
            "key": {
                "P": "minecraft:oak_planks"
            }
        }
        """
        val (_, ingredients) = assertResultSuccess(parse(json))
        assertEquals(1, ingredients.size)
        assertEquals(listOf("minecraft:oak_planks"), ingredients[0])
    }

    @Test
    fun `parses key objects with item field`() {
        val json = """
        {
            "result": "minecraft:furnace",
            "key": {
                "#": {"item": "minecraft:cobblestone"}
            }
        }
        """
        val (_, ingredients) = assertResultSuccess(parse(json))
        assertEquals(1, ingredients.size)
        assertEquals(listOf("minecraft:cobblestone"), ingredients[0])
    }

    @Test
    fun `parses key objects with key field`() {
        val json = """
        {
            "result": "minecraft:piston",
            "key": {
                "R": {"key": "minecraft:redstone"}
            }
        }
        """
        val (_, ingredients) = assertResultSuccess(parse(json))
        assertEquals(1, ingredients.size)
        assertEquals(listOf("minecraft:redstone"), ingredients[0])
    }

    @Test
    fun `parses key objects with id field`() {
        val json = """
        {
            "result": "minecraft:lever",
            "key": {
                "S": {"id": "minecraft:stick"}
            }
        }
        """
        val (_, ingredients) = assertResultSuccess(parse(json))
        assertEquals(1, ingredients.size)
        assertEquals(listOf("minecraft:stick"), ingredients[0])
    }

    @Test
    fun `parses key objects with tag field`() {
        val json = """
        {
            "result": "minecraft:campfire",
            "key": {
                "L": {"tag": "minecraft:logs"}
            }
        }
        """
        val (_, ingredients) = assertResultSuccess(parse(json))
        assertEquals(1, ingredients.size)
        assertEquals(listOf("#minecraft:logs"), ingredients[0])
    }

    @Test
    fun `parses key with array values`() {
        val json = """
        {
            "result": "minecraft:bed",
            "key": {
                "W": ["minecraft:oak_planks", "minecraft:birch_planks"]
            }
        }
        """
        val (_, ingredients) = assertResultSuccess(parse(json))
        assertEquals(1, ingredients.size)
        assertEquals(2, ingredients[0].size)
    }

    @Test
    fun `fails when both ingredients and key are missing`() {
        val json = """{"result": "minecraft:stone"}"""
        assertResultFailure(parse(json))
    }

    @Test
    fun `fails when result id is missing`() {
        val json = """{"ingredients": ["minecraft:stone"]}"""
        assertResultFailure(parse(json))
    }
}
