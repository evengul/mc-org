package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceQuantity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RecipeQuantityParserTest {

    private fun parseQuantity(jsonString: String): ResourceQuantity = runBlocking {
        RecipeQuantityParser.parseResultQuantity(Json.parseToJsonElement(jsonString), "test.json")
    }

    @Test
    fun `extracts count from result object`() {
        val result = parseQuantity("""{"result": {"id": "minecraft:oak_planks", "count": 4}}""")
        assertEquals(ResourceQuantity.ItemQuantity(4), result)
    }

    @Test
    fun `defaults to 1 when count is missing`() {
        val result = parseQuantity("""{"result": {"id": "minecraft:stick"}}""")
        assertEquals(ResourceQuantity.ItemQuantity(1), result)
    }

    @Test
    fun `defaults to 1 when result is primitive`() {
        val result = parseQuantity("""{"result": "minecraft:stone"}""")
        assertEquals(ResourceQuantity.ItemQuantity(1), result)
    }

    @Test
    fun `defaults to 1 for count of 0`() {
        val result = parseQuantity("""{"result": {"count": 0}}""")
        assertEquals(ResourceQuantity.ItemQuantity(1), result)
    }

    @Test
    fun `defaults to 1 for negative count`() {
        val result = parseQuantity("""{"result": {"count": -1}}""")
        assertEquals(ResourceQuantity.ItemQuantity(1), result)
    }

    @Test
    fun `handles large count`() {
        val result = parseQuantity("""{"result": {"count": 64}}""")
        assertEquals(ResourceQuantity.ItemQuantity(64), result)
    }

    @Test
    fun `ingredientQuantity returns 1`() {
        assertEquals(ResourceQuantity.ItemQuantity(1), RecipeQuantityParser.ingredientQuantity())
    }
}
