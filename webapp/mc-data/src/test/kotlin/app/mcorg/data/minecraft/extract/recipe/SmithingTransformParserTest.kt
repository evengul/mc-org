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

class SmithingTransformParserTest {

    private fun parse(jsonString: String): Result<*, ResourceSource> = runBlocking {
        SmithingTransformParser.parse(Json.parseToJsonElement(jsonString), "test.json")
    }

    @Test
    fun `parses basic smithing transform with primitive fields`() {
        val json = """
        {
            "base": "minecraft:diamond_sword",
            "addition": "minecraft:netherite_ingot",
            "result": "minecraft:netherite_sword"
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceSource.SourceType.RecipeTypes.SMITHING_TRANSFORM, source.type)
        assertEquals(2, source.requiredItems.size)

        val byId = source.requiredItems.associate { it.first.id to it.second }
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:diamond_sword"])
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:netherite_ingot"])

        assertEquals(1, source.producedItems.size)
        assertEquals("minecraft:netherite_sword", source.producedItems[0].first.id)
    }

    @Test
    fun `parses base with key fallback`() {
        val json = """
        {
            "base": {"key": "minecraft:diamond_chestplate"},
            "addition": "minecraft:netherite_ingot",
            "result": "minecraft:netherite_chestplate"
        }
        """
        val source = assertResultSuccess(parse(json))
        val byId = source.requiredItems.associate { it.first.id to it.second }
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:diamond_chestplate"])
    }

    @Test
    fun `parses base with item fallback`() {
        val json = """
        {
            "base": {"item": "minecraft:diamond_helmet"},
            "addition": "minecraft:netherite_ingot",
            "result": "minecraft:netherite_helmet"
        }
        """
        val source = assertResultSuccess(parse(json))
        val byId = source.requiredItems.associate { it.first.id to it.second }
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:diamond_helmet"])
    }

    @Test
    fun `parses addition with key fallback`() {
        val json = """
        {
            "base": "minecraft:diamond_pickaxe",
            "addition": {"key": "minecraft:netherite_ingot"},
            "result": "minecraft:netherite_pickaxe"
        }
        """
        val source = assertResultSuccess(parse(json))
        val byId = source.requiredItems.associate { it.first.id to it.second }
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:netherite_ingot"])
    }

    @Test
    fun `parses addition with item fallback`() {
        val json = """
        {
            "base": "minecraft:diamond_axe",
            "addition": {"item": "minecraft:netherite_ingot"},
            "result": "minecraft:netherite_axe"
        }
        """
        val source = assertResultSuccess(parse(json))
        val byId = source.requiredItems.associate { it.first.id to it.second }
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:netherite_ingot"])
    }

    @Test
    fun `fails when base is missing`() {
        val json = """
        {
            "addition": "minecraft:netherite_ingot",
            "result": "minecraft:netherite_sword"
        }
        """
        assertResultFailure(parse(json))
    }

    @Test
    fun `fails when addition is missing`() {
        val json = """
        {
            "base": "minecraft:diamond_sword",
            "result": "minecraft:netherite_sword"
        }
        """
        assertResultFailure(parse(json))
    }

    @Test
    fun `fails when result is missing`() {
        val json = """
        {
            "base": "minecraft:diamond_sword",
            "addition": "minecraft:netherite_ingot"
        }
        """
        assertResultFailure(parse(json))
    }
}
