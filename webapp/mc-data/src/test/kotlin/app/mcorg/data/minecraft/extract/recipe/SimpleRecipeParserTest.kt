package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.data.minecraft.TestUtils.assertResultFailure
import app.mcorg.data.minecraft.TestUtils.assertResultSuccess
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SimpleRecipeParserTest {

    private fun parse(
        jsonString: String,
        sourceType: ResourceSource.SourceType = ResourceSource.SourceType.RecipeTypes.SMELTING
    ): Result<*, ResourceSource> = runBlocking {
        SimpleRecipeParser.parse(Json.parseToJsonElement(jsonString), sourceType, "test.json")
    }

    @Test
    fun `parses ingredient as primitive`() {
        val json = """
        {
            "ingredient": "minecraft:iron_ore",
            "result": "minecraft:iron_ingot"
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceSource.SourceType.RecipeTypes.SMELTING, source.type)
        assertEquals(1, source.requiredItems.size)
        assertEquals("minecraft:iron_ore", source.requiredItems[0].first.id)
        assertEquals(ResourceQuantity.ItemQuantity(1), source.requiredItems[0].second)
    }

    @Test
    fun `parses ingredient as object with value field`() {
        val json = """
        {
            "ingredient": {"value": "minecraft:gold_ore"},
            "result": "minecraft:gold_ingot"
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals("minecraft:gold_ore", source.requiredItems[0].first.id)
    }

    @Test
    fun `parses ingredient as object with id field`() {
        val json = """
        {
            "ingredient": {"id": "minecraft:copper_ore"},
            "result": "minecraft:copper_ingot"
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals("minecraft:copper_ore", source.requiredItems[0].first.id)
    }

    @Test
    fun `parses ingredient as object with key field`() {
        val json = """
        {
            "ingredient": {"key": "minecraft:raw_iron"},
            "result": "minecraft:iron_ingot"
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals("minecraft:raw_iron", source.requiredItems[0].first.id)
    }

    @Test
    fun `parses ingredient as object with item field`() {
        val json = """
        {
            "ingredient": {"item": "minecraft:raw_gold"},
            "result": "minecraft:gold_ingot"
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals("minecraft:raw_gold", source.requiredItems[0].first.id)
    }

    @Test
    fun `parses ingredient as object with tag field`() {
        val json = """
        {
            "ingredient": {"tag": "minecraft:logs"},
            "result": "minecraft:charcoal"
        }
        """
        val source = assertResultSuccess(parse(json))
        assertIs<MinecraftTag>(source.requiredItems[0].first)
        assertEquals("#minecraft:logs", source.requiredItems[0].first.id)
    }

    @Test
    fun `parses ingredient as array of primitives`() {
        val json = """
        {
            "ingredient": ["minecraft:iron_ore", "minecraft:deepslate_iron_ore"],
            "result": "minecraft:iron_ingot"
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(2, source.requiredItems.size)
        val ids = source.requiredItems.map { it.first.id }.toSet()
        assertEquals(setOf("minecraft:iron_ore", "minecraft:deepslate_iron_ore"), ids)
    }

    @Test
    fun `parses ingredient array with objects`() {
        val json = """
        {
            "ingredient": [
                {"key": "minecraft:iron_ore"},
                {"key": "minecraft:deepslate_iron_ore"}
            ],
            "result": "minecraft:iron_ingot"
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(2, source.requiredItems.size)
    }

    @Test
    fun `uses correct source type`() {
        val json = """
        {
            "ingredient": "minecraft:cobblestone",
            "result": "minecraft:stone"
        }
        """
        assertEquals(
            ResourceSource.SourceType.RecipeTypes.BLASTING,
            assertResultSuccess(parse(json, ResourceSource.SourceType.RecipeTypes.BLASTING)).type
        )
        assertEquals(
            ResourceSource.SourceType.RecipeTypes.SMOKING,
            assertResultSuccess(parse(json, ResourceSource.SourceType.RecipeTypes.SMOKING)).type
        )
        assertEquals(
            ResourceSource.SourceType.RecipeTypes.CAMPFIRE_COOKING,
            assertResultSuccess(parse(json, ResourceSource.SourceType.RecipeTypes.CAMPFIRE_COOKING)).type
        )
        assertEquals(
            ResourceSource.SourceType.RecipeTypes.STONECUTTING,
            assertResultSuccess(parse(json, ResourceSource.SourceType.RecipeTypes.STONECUTTING)).type
        )
    }

    @Test
    fun `parses result quantity`() {
        val json = """
        {
            "ingredient": "minecraft:oak_log",
            "result": {"id": "minecraft:charcoal", "count": 2}
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceQuantity.ItemQuantity(2), source.producedItems[0].second)
    }

    @Test
    fun `each ingredient has quantity 1`() {
        val json = """
        {
            "ingredient": ["minecraft:iron_ore", "minecraft:deepslate_iron_ore"],
            "result": "minecraft:iron_ingot"
        }
        """
        val source = assertResultSuccess(parse(json))
        source.requiredItems.forEach {
            assertEquals(ResourceQuantity.ItemQuantity(1), it.second)
        }
    }

    @Test
    fun `fails when ingredient is missing`() {
        val json = """{"result": "minecraft:iron_ingot"}"""
        assertResultFailure(parse(json))
    }

    @Test
    fun `fails when result is missing`() {
        val json = """{"ingredient": "minecraft:iron_ore"}"""
        assertResultFailure(parse(json))
    }

    @Test
    fun `produced item is Item type`() {
        val json = """
        {
            "ingredient": "minecraft:iron_ore",
            "result": "minecraft:iron_ingot"
        }
        """
        val source = assertResultSuccess(parse(json))
        assertIs<Item>(source.producedItems[0].first)
    }
}
