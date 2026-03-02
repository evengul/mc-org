package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.data.minecraft.TestUtils
import app.mcorg.data.minecraft.extract.ServerFileTest
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertIsNot
import kotlin.test.assertNotEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExtractRecipesStepTest : ServerFileTest(
    MinecraftVersionRange.Unbounded
) {
    @ParameterizedTest
    @MethodSource("getVersions")
    fun getRecipes(version: MinecraftVersion.Release) {
        val recipes = TestUtils.executeAndAssertSuccess(
            ExtractRecipesStep,
            version to versionPath(version)
        )

        assertNotEquals(0, recipes.size)

        val byType = recipes.groupBy { it.type }.mapValues { it.value.size }
        when {
            version > MinecraftVersion.Release(1, 21, 1) -> assertEquals(9, byType.size)
            else -> assertEquals(8, byType.size)
        }
        byType.forEach { (_, recipes) ->
            assertNotEquals(0, recipes)
        }

        recipes.forEach { recipe ->
            recipe.requiredItems.forEach { item ->
                assertNotEquals("", item.first.name)
                assertNotEquals(item.first.id, item.first.name)

                assertIsNot<ResourceQuantity.Unknown>(item.second)

                when (val id = item.first) {
                    is Item -> assertNotEquals('#', id.id.first())
                    is MinecraftTag -> assertNotEquals(0, id.content.size, "Tag ${id.id} does not have content")
                }
            }

            recipe.producedItems.forEach { item ->
                assertNotEquals("", item.first.name)
                assertNotEquals(item.first.id, item.first.name)

                assertIsNot<ResourceQuantity.Unknown>(item.second)

                when (val id = item.first) {
                    is Item -> assertNotEquals('#', id.id.first())
                    is MinecraftTag -> assertNotEquals(0, id.content.size)
                }
            }
        }
    }
}
