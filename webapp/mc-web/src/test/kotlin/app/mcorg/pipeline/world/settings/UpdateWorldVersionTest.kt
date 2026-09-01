package app.mcorg.pipeline.world.settings

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.TestUtils
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.world.settings.general.ValidateWorldVersionInputStep
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-157 changed what this step means. It used to ask only "does this string parse as a Minecraft
 * version", which accepted any well-formed string — including one this instance has never ingested,
 * leaving the world pointing at an empty item catalog with nothing to say why. It now accepts only
 * a version we actually hold data for.
 *
 * With no database these tests run against [MinecraftVersion.supportedVersions_backup], the same
 * fallback the step itself lands on, which is why the accepted set here is 1.20.0 / 1.21.0 / 1.21.4
 * rather than a list of everything Mojang has shipped.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpdateWorldVersionTest {

    private fun createParameters(vararg pairs: Pair<String, String>): Parameters {
        val builder = ParametersBuilder()
        pairs.forEach { (key, value) -> builder.append(key, value) }
        return builder.build()
    }

    @ParameterizedTest
    @ValueSource(strings = ["1.20.0", "1.21.0", "1.21.4"])
    fun `an ingested release is accepted`(version: String) {
        runBlocking {
            val result = TestUtils.executeAndAssertSuccess(
                ValidateWorldVersionInputStep,
                createParameters("version" to version),
            )

            assertEquals(MinecraftVersion.fromString(version), result)
        }
    }

    @Test
    fun `surrounding whitespace is trimmed rather than failing the match`() {
        runBlocking {
            val result = TestUtils.executeAndAssertSuccess(
                ValidateWorldVersionInputStep,
                createParameters("version" to "  1.21.4  "),
            )

            assertEquals(MinecraftVersion.fromString("1.21.4"), result)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["1.19.4", "1.17.1", "1.99.0"])
    fun `a well-formed release we have not ingested is rejected`(version: String) {
        // The whole point of the change: these parse perfectly and are still not usable, because
        // there are no items, recipes or loot tables behind them.
        runBlocking {
            val result = TestUtils.executeAndAssertFailure(
                ValidateWorldVersionInputStep,
                createParameters("version" to version),
            )

            assertTrue(
                result.errors.any { it is ValidationFailure.InvalidValue && it.parameterName == "version" },
                "expected an InvalidValue for an uningested version, got ${result.errors}",
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["23w31a", "23w07a", "22w46a"])
    fun `a snapshot is rejected because worlds plan against ingested releases`(version: String) {
        // Ingestion produces releases only, so a snapshot could never have a catalog to plan
        // against — it is now refused at the door rather than accepted and then found empty.
        runBlocking {
            val result = TestUtils.executeAndAssertFailure(
                ValidateWorldVersionInputStep,
                createParameters("version" to version),
            )

            assertTrue(
                result.errors.any { it is ValidationFailure.InvalidValue && it.parameterName == "version" },
                "expected an InvalidValue for a snapshot, got ${result.errors}",
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["invalid.version", "not-a-version", "1.2.3.4", "snapshot-invalid", "99w99z", "null"])
    fun `a malformed version is rejected`(version: String) {
        runBlocking {
            val result = TestUtils.executeAndAssertFailure(
                ValidateWorldVersionInputStep,
                createParameters("version" to version),
            )

            assertTrue(
                result.errors.any { it is ValidationFailure.InvalidValue && it.parameterName == "version" },
                "expected an InvalidValue for a malformed version, got ${result.errors}",
            )
        }
    }

    @Test
    fun `a missing version is a missing parameter, not an invalid one`() {
        runBlocking {
            val result = TestUtils.executeAndAssertFailure(
                ValidateWorldVersionInputStep,
                createParameters(),
            )

            assertTrue(
                result.errors.any {
                    it is ValidationFailure.MissingParameter && it.parameterName == "version"
                },
                "expected a MissingParameter, got ${result.errors}",
            )
        }
    }

    @Test
    fun `an empty version is a missing parameter`() {
        runBlocking {
            val result = TestUtils.executeAndAssertFailure(
                ValidateWorldVersionInputStep,
                createParameters("version" to ""),
            )

            assertTrue(
                result.errors.any {
                    it is ValidationFailure.MissingParameter && it.parameterName == "version"
                },
                "expected a MissingParameter, got ${result.errors}",
            )
        }
    }
}
