package app.mcorg.pipeline.minecraft.extract.loot

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.test.utils.TestUtils
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

class ExtractLootTablesTest {

    @Test
    fun getLootTables() {
        val loot = TestUtils.executeAndAssertSuccess(
            ExtractLootTables,
            MinecraftVersion.Release(1, 21, 11) to Path.of("src/test/resources/servers/extracted")
        )

        assertEquals(1277, loot.size)
    }

}