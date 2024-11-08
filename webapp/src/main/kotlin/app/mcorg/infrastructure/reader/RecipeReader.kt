package app.mcorg.infrastructure.reader

import app.mcorg.infrastructure.reader.entities.recipe.*
import kotlinx.serialization.json.Json

class RecipeReader : DirectoryReader<RecipeEntity>("minecraft/recipe") {
    override fun parseContent(content: String): RecipeEntity {
        try {
            return when {
                content.contains("minecraft:crafting_shaped") -> Json.decodeFromString<ShapedRecipeEntity>(content)
                content.contains("minecraft:crafting_shapeless") -> Json.decodeFromString<ShapelessCraftingRecipeEntity>(content)
                content.contains("minecraft:smelting") -> Json.decodeFromString<SmeltingRecipeEntity>(content)
                content.contains("minecraft:blasting") -> Json.decodeFromString<BlastFurnaceRecipeEntity>(content)
                content.contains("minecraft:smoking") -> Json.decodeFromString<SmokingRecipeEntity>(content)
                content.contains("minecraft:campfire_cooking") -> Json.decodeFromString<CampfireCookingRecipeEntity>(content)
                content.contains("minecraft:stonecutting") -> Json.decodeFromString<StonecuttingRecipeEntity>(content)
                content.contains("minecraft:crafting_transmute") -> Json.decodeFromString<TransmutationRecipeEntity>(content)
                content.contains("minecraft:smithing_transform") -> Json.decodeFromString<SmithingTransformRecipeEntity>(content)
                content.contains("minecraft:smithing_trim") -> Json.decodeFromString<SmithingTrimRecipeEntity>(content)
                content.contains("minecraft:crafting_special_") ||
                        content.contains("minecraft:crafting_decorated_pot") -> EmptyRecipeEntity()
                else -> throw RuntimeException("Unknown crafting recipe type: $content")
            }
        } catch (e: Exception) {
            println("ERROR in file: $content")
            throw e
        }
    }
}