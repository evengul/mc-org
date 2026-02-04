package app.mcorg.pipeline.minecraft.extract.loot

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.pipeline.minecraft.extract.ServerFileTest
import app.mcorg.test.utils.TestUtils
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExtractLootTablesTest : ServerFileTest() {

    @ParameterizedTest
    @MethodSource("getVersions")
    fun getLootTables(version: MinecraftVersion.Release) {

        val loot = TestUtils.executeAndAssertSuccess(
            ExtractLootTables,
            version to Path.of("src/test/resources/servers/extracted/${version.toString().replace(".0", "")}")
        )

        assertNotEquals(0, loot.size)

        loot.forEach { source ->
            source.requiredItems.forEach {
                assertNotEquals(it.first.id, it.first.name, "Item id and name are equal for id: ${it.first.id} in loot table: ${source.filename}")
                assertNotEquals("", it.first.name, "Item name is empty for id: ${it.first.id} in loot table: ${source.filename}")

                assertIs<ResourceQuantity.Unknown>(it.second)
            }
            source.producedItems.forEach {
                assertNotEquals(it.first.id, it.first.name, "Item id and name are equal for id: ${it.first.id} in loot table: ${source.filename}")
                assertNotEquals("", it.first.name, "Item name is empty for id: ${it.first.id} in loot table: ${source.filename}")

                assertIs<ResourceQuantity.Unknown>(it.second)
            }
        }
    }
}