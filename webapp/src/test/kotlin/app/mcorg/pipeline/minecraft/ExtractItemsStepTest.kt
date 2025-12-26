package app.mcorg.pipeline.minecraft

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.test.utils.TestUtils
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertNotEquals

class ExtractItemsStepTest {

    private val directory = "src/test/resources/extracted_server_data"

    @Test
    fun testExtractItems() {
        val result = TestUtils.executeAndAssertSuccess(ExtractItemsStep, MinecraftVersion.Release(1, 21, 11) to Path.of(directory))

        assertNotEquals(result.size, 0)
        result.forEach { assertNotEquals(it.id, it.name) }
    }

}