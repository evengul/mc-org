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

class TransmuteRecipeParserTest {

    private fun parse(jsonString: String): Result<*, ResourceSource> = runBlocking {
        TransmuteRecipeParser.parse(Json.parseToJsonElement(jsonString), "test.json")
    }

    @Test
    fun `parses basic transmute recipe`() {
        val json = """
        {
            "input": "minecraft:copper_ingot",
            "material": "minecraft:honeycomb",
            "result": "minecraft:waxed_copper_block"
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceSource.SourceType.RecipeTypes.CRAFTING_TRANSMUTE, source.type)
        assertEquals(2, source.requiredItems.size)

        val byId = source.requiredItems.associate { it.first.id to it.second }
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:copper_ingot"])
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:honeycomb"])

        assertEquals(1, source.producedItems.size)
        assertEquals("minecraft:waxed_copper_block", source.producedItems[0].first.id)
    }

    @Test
    fun `parses result quantity`() {
        val json = """
        {
            "input": "minecraft:copper_ingot",
            "material": "minecraft:honeycomb",
            "result": {"id": "minecraft:waxed_copper_block", "count": 4}
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceQuantity.ItemQuantity(4), source.producedItems[0].second)
    }

    @Test
    fun `fails when input is missing`() {
        val json = """
        {
            "material": "minecraft:honeycomb",
            "result": "minecraft:waxed_copper_block"
        }
        """
        assertResultFailure(parse(json))
    }

    @Test
    fun `fails when material is missing`() {
        val json = """
        {
            "input": "minecraft:copper_ingot",
            "result": "minecraft:waxed_copper_block"
        }
        """
        assertResultFailure(parse(json))
    }

    @Test
    fun `fails when result is missing`() {
        val json = """
        {
            "input": "minecraft:copper_ingot",
            "material": "minecraft:honeycomb"
        }
        """
        assertResultFailure(parse(json))
    }
}
