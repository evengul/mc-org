package app.mcorg.data.minecraft

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.data.minecraft.extract.ExtractItemsStep
import app.mcorg.data.minecraft.extract.ServerFileTest
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExtractItemsStepTest : ServerFileTest() {

    @ParameterizedTest
    @MethodSource("getVersions")
    fun testExtractItems(version: MinecraftVersion.Release) {
        val result = TestUtils.executeAndAssertSuccess(ExtractItemsStep, contextFor(version))

        assertNotEquals(result.size, 0)

        // The registry comes from the lang file, so untagged, recipe-less items must be present.
        assertTrue(
            result.any { it.id == "minecraft:creeper_spawn_egg" },
            "Expected lang-derived registry to include untagged items for $version"
        )

        result.forEach { item ->
            assertNotEquals("", item.name, "Item ${item.id} has an empty name")
        }
    }
}
