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

class ShapedRecipeParserTest {

    private fun parse(jsonString: String): Result<*, ResourceSource> = runBlocking {
        ShapedRecipeParser.parse(Json.parseToJsonElement(jsonString), "test.json")
    }

    @Test
    fun `parses basic shaped recipe with pattern and primitive keys`() {
        val json = """
        {
            "result": "minecraft:crafting_table",
            "pattern": ["##", "##"],
            "key": {
                "#": "minecraft:oak_planks"
            }
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, source.type)
        assertEquals("test.json", source.filename)

        assertEquals(1, source.producedItems.size)
        assertIs<Item>(source.producedItems[0].first)
        assertEquals("minecraft:crafting_table", source.producedItems[0].first.id)

        assertEquals(1, source.requiredItems.size)
        assertEquals("minecraft:oak_planks", source.requiredItems[0].first.id)
        assertEquals(ResourceQuantity.ItemQuantity(4), source.requiredItems[0].second)
    }

    @Test
    fun `counts pattern symbols correctly`() {
        val json = """
        {
            "result": "minecraft:chest",
            "pattern": ["###", "# #", "###"],
            "key": {
                "#": "minecraft:oak_planks"
            }
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(1, source.requiredItems.size)
        assertEquals(ResourceQuantity.ItemQuantity(8), source.requiredItems[0].second)
    }

    @Test
    fun `handles multiple key symbols`() {
        val json = """
        {
            "result": "minecraft:piston",
            "pattern": ["TTT", "#X#", "#R#"],
            "key": {
                "T": "minecraft:oak_planks",
                "X": "minecraft:iron_ingot",
                "R": "minecraft:redstone",
                "#": "minecraft:cobblestone"
            }
        }
        """
        val source = assertResultSuccess(parse(json))
        val byId = source.requiredItems.associate { it.first.id to it.second }

        assertEquals(ResourceQuantity.ItemQuantity(3), byId["minecraft:oak_planks"])
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:iron_ingot"])
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:redstone"])
        assertEquals(ResourceQuantity.ItemQuantity(4), byId["minecraft:cobblestone"])
    }

    @Test
    fun `parses key with item field`() {
        val json = """
        {
            "result": "minecraft:furnace",
            "pattern": ["###", "# #", "###"],
            "key": {
                "#": {"item": "minecraft:cobblestone"}
            }
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals("minecraft:cobblestone", source.requiredItems[0].first.id)
    }

    @Test
    fun `parses key with tag field as MinecraftTag`() {
        val json = """
        {
            "result": "minecraft:chest",
            "pattern": ["###", "# #", "###"],
            "key": {
                "#": {"tag": "minecraft:planks"}
            }
        }
        """
        val source = assertResultSuccess(parse(json))
        assertIs<MinecraftTag>(source.requiredItems[0].first)
        assertEquals("#minecraft:planks", source.requiredItems[0].first.id)
    }

    @Test
    fun `parses key with key field`() {
        val json = """
        {
            "result": "minecraft:lever",
            "pattern": ["S", "#"],
            "key": {
                "S": {"key": "minecraft:stick"},
                "#": {"key": "minecraft:cobblestone"}
            }
        }
        """
        val source = assertResultSuccess(parse(json))
        val byId = source.requiredItems.associate { it.first.id to it.second }
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:stick"])
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:cobblestone"])
    }

    @Test
    fun `parses key with id field`() {
        val json = """
        {
            "result": "minecraft:hopper",
            "pattern": ["I I", "ICI", " I "],
            "key": {
                "I": {"id": "minecraft:iron_ingot"},
                "C": {"id": "minecraft:chest"}
            }
        }
        """
        val source = assertResultSuccess(parse(json))
        val byId = source.requiredItems.associate { it.first.id to it.second }
        assertEquals(ResourceQuantity.ItemQuantity(5), byId["minecraft:iron_ingot"])
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:chest"])
    }

    @Test
    fun `parses result quantity from count field`() {
        val json = """
        {
            "result": {"id": "minecraft:oak_planks", "count": 4},
            "pattern": ["#"],
            "key": {
                "#": "minecraft:oak_log"
            }
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceQuantity.ItemQuantity(4), source.producedItems[0].second)
    }

    @Test
    fun `defaults quantity to 1 when count missing`() {
        val json = """
        {
            "result": "minecraft:stick",
            "pattern": ["#", "#"],
            "key": {
                "#": "minecraft:oak_planks"
            }
        }
        """
        val source = assertResultSuccess(parse(json))
        assertEquals(ResourceQuantity.ItemQuantity(1), source.producedItems[0].second)
    }

    @Test
    fun `ignores spaces in pattern`() {
        val json = """
        {
            "result": "minecraft:wooden_sword",
            "pattern": [" # ", " # ", " S "],
            "key": {
                "#": "minecraft:oak_planks",
                "S": "minecraft:stick"
            }
        }
        """
        val source = assertResultSuccess(parse(json))
        val byId = source.requiredItems.associate { it.first.id to it.second }
        assertEquals(ResourceQuantity.ItemQuantity(2), byId["minecraft:oak_planks"])
        assertEquals(ResourceQuantity.ItemQuantity(1), byId["minecraft:stick"])
    }

    @Test
    fun `fails when pattern is missing`() {
        val json = """
        {
            "result": "minecraft:stone",
            "key": {"#": "minecraft:cobblestone"}
        }
        """
        assertResultFailure(parse(json))
    }

    @Test
    fun `fails when key is missing`() {
        val json = """
        {
            "result": "minecraft:stone",
            "pattern": ["###"]
        }
        """
        assertResultFailure(parse(json))
    }

    @Test
    fun `fails when result is missing`() {
        val json = """
        {
            "pattern": ["###"],
            "key": {"#": "minecraft:cobblestone"}
        }
        """
        assertResultFailure(parse(json))
    }
}
