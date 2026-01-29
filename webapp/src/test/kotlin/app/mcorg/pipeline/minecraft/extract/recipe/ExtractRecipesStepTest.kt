package app.mcorg.pipeline.minecraft.extract.recipe

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.minecraft.extract.ServerFileTest
import app.mcorg.test.utils.TestUtils
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.test.assertEquals
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
            version to Path.of("src/test/resources/servers/extracted/${version.toString().replace(".0", "")}")
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
                // Ensure names have been resolved
                assertNotEquals("", item.name)
                assertNotEquals(item.id, item.name)

                // Ensure no item ids with tag references exist
                if (item is Item) {
                    assertNotEquals('#', item.id.first())
                }
            }
            recipe.producedItems.forEach { item ->
                // Ensure names have been resolved
                assertNotEquals("", item.name)
                assertNotEquals(item.id, item.name)

                // Ensure no item ids with tag references exist
                if (item is Item) {
                    assertNotEquals('#', item.id.first())
                }
            }
        }
    }
}