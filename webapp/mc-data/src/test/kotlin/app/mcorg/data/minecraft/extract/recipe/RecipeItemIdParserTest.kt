package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.data.minecraft.TestUtils.assertResultFailure
import app.mcorg.data.minecraft.TestUtils.assertResultSuccess
import app.mcorg.pipeline.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RecipeItemIdParserTest {

    private fun parse(jsonString: String): Result<*, String> = runBlocking {
        RecipeItemIdParser.parse(Json.parseToJsonElement(jsonString), "test.json")
    }

    @Test
    fun `parses id at top level`() {
        val result = parse("""{"id": "minecraft:stone"}""")
        assertEquals("minecraft:stone", assertResultSuccess(result))
    }

    @Test
    fun `parses result as primitive`() {
        val result = parse("""{"result": "minecraft:oak_planks"}""")
        assertEquals("minecraft:oak_planks", assertResultSuccess(result))
    }

    @Test
    fun `parses result value as primitive`() {
        val result = parse("""{"result": {"value": "minecraft:iron_ingot"}}""")
        assertEquals("minecraft:iron_ingot", assertResultSuccess(result))
    }

    @Test
    fun `parses result id`() {
        val result = parse("""{"result": {"id": "minecraft:diamond"}}""")
        assertEquals("minecraft:diamond", assertResultSuccess(result))
    }

    @Test
    fun `parses result key`() {
        val result = parse("""{"result": {"key": "minecraft:gold_ingot"}}""")
        assertEquals("minecraft:gold_ingot", assertResultSuccess(result))
    }

    @Test
    fun `parses result item`() {
        val result = parse("""{"result": {"item": "minecraft:stick"}}""")
        assertEquals("minecraft:stick", assertResultSuccess(result))
    }

    @Test
    fun `parses result value id`() {
        val result = parse("""{"result": {"value": {"id": "minecraft:emerald"}}}""")
        assertEquals("minecraft:emerald", assertResultSuccess(result))
    }

    @Test
    fun `prefers id over result`() {
        val result = parse("""{"id": "minecraft:stone", "result": "minecraft:dirt"}""")
        assertEquals("minecraft:stone", assertResultSuccess(result))
    }

    @Test
    fun `prefers result primitive over result object`() {
        val result = parse("""{"result": "minecraft:oak_planks"}""")
        assertEquals("minecraft:oak_planks", assertResultSuccess(result))
    }

    @Test
    fun `fails when no id found`() {
        val result = parse("""{"type": "minecraft:crafting_shaped"}""")
        assertResultFailure(result)
    }

    @Test
    fun `fails on empty object`() {
        val result = parse("""{}""")
        assertResultFailure(result)
    }

    @Test
    fun `fails when result is object without known keys`() {
        val result = parse("""{"result": {"unknown": "minecraft:stone"}}""")
        assertResultFailure(result)
    }
}
