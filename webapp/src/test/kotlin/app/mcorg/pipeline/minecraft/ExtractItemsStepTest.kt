package app.mcorg.pipeline.minecraft

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.minecraft.extract.ExtractItemsStep
import app.mcorg.pipeline.minecraft.extract.ServerFileTest
import app.mcorg.test.utils.TestUtils
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertNotEquals


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExtractItemsStepTest : ServerFileTest() {

    @ParameterizedTest
    @MethodSource("getVersions")
    fun testExtractItems(version: MinecraftVersion.Release) {
        val result = TestUtils.executeAndAssertSuccess(ExtractItemsStep, version to versionPath(version))

        assertNotEquals(result.size, 0)
    }

}