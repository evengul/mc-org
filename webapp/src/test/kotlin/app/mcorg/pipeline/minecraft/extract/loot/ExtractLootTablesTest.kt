package app.mcorg.pipeline.minecraft.extract.loot

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.minecraft.extract.ServerFileTest
import app.mcorg.test.utils.TestUtils
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
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
                assertNotEquals(it.id, it.name, "Item id and name are equal for id: ${it.id} in loot table: ${source.filename}")
                assertNotEquals("", it.name, "Item name is empty for id: ${it.id} in loot table: ${source.filename}")
            }
            source.producedItems.forEach {
                assertNotEquals(it.id, it.name, "Item id and name are equal for id: ${it.id} in loot table: ${source.filename}")
                assertNotEquals("", it.name, "Item name is empty for id: ${it.id} in loot table: ${source.filename}")
            }
        }
    }
}