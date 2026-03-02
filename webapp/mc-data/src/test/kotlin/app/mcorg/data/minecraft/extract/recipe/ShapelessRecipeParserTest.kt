package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.data.minecraft.TestUtils.assertResultFailure
import app.mcorg.data.minecraft.TestUtils.assertResultSuccess
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ShapelessRecipeParserTest {

    private fun parse(jsonString: String): Result<*, ResourceSource> = runBlocking {
        ShapelessRecipeParser.parse(Json.parseToJsonElement(jsonString), "test.json")
    }

    @Test
    fun `parses basic shapeless recipe`() {
        val json = """
        {
            "result": "minecraft:oak_planks",
            "ingredients": ["minecraft:oak_log"]
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS, source.type)
        assertEquals(1, source.requiredItems.size)
        assertEquals("minecraft:oak_log", source.requiredItems[0].first.id)
        assertEquals(ResourceQuantity.ItemQuantity(1), source.requiredItems[0].second)
    }

    @Test
    fun `groups duplicate ingredients with count`() {
        val json = """
        {
            "result": "minecraft:fire_charge",
            "ingredients": ["minecraft:gunpowder", "minecraft:coal", "minecraft:gunpowder"]
        }
        """
        val source = assertResultSuccess(parse(json))
        val byId = source.requiredItems.associate { it.first.id to it.second }
        assertEquals(ResourceQuantity.ItemQuantity(2), byId["minecraft:gunpowder"])
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:coal"])
    }

    @Test
    fun `parses result quantity`() {
        val json = """
        {
            "result": {"id": "minecraft:oak_planks", "count": 4},
            "ingredients": ["minecraft:oak_log"]
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceQuantity.ItemQuantity(4), source.producedItems[0].second)
        assertEquals("minecraft:oak_planks", source.producedItems[0].first.id)
    }

    @Test
    fun `defaults result quantity to 1`() {
        val json = """
        {
            "result": "minecraft:oak_button",
            "ingredients": ["minecraft:oak_planks"]
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceQuantity.ItemQuantity(1), source.producedItems[0].second)
    }

    @Test
    fun `fails when ingredients missing`() {
        val json = """{"result": "minecraft:stone"}"""
        assertResultFailure(parse(json))
    }

    @Test
    fun `fails when result missing`() {
        val json = """{"ingredients": ["minecraft:stone"]}"""
        assertResultFailure(parse(json))
    }
}
