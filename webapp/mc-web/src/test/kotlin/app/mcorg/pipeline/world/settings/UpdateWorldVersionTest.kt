package app.mcorg.pipeline.world.settings

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.TestUtils
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.world.settings.general.ValidateWorldVersionInputStep
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
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
 * MCO-558: the ingested set is seeded here rather than read from wherever this happens to run.
 * `GetSupportedVersionsStep` answers from `CacheManager.supportedVersions` when it is warm and only
 * queries `minecraft_version` when it is not — so with the cache primed this class never opens a
 * connection, and "ingested" means exactly [ingested].
 *
 * Left to the database it meant whatever that database held, and the unit tier does not opt out of
 * having one: nothing exports `DB_*` for it, so `readConfig()` falls back to its built-in
 * `localhost:5432/postgres` — the shared Docker dev database, which carries production's ingested
 * catalog including 1.19.4. That made this class green in CI (no postgres on the unit runner, so
 * the step fell through to `MinecraftVersion.supportedVersions_backup`) and red on any machine with
 * `start-db.sh` running, worktree or not.
 *
 * The end-to-end version switch — against a database, with the uningested example derived from it
 * rather than named — is `UpdateWorldVersionIT`'s.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpdateWorldVersionTest {

    private val ingested = listOf(
        MinecraftVersion.release(20, 0),
        MinecraftVersion.release(21, 0),
        MinecraftVersion.release(21, 4),
    )

    @BeforeEach
    fun primeSupportedVersions() {
        CacheManager.supportedVersions.put("versions", ingested)
    }

    @AfterAll
    fun clearSupportedVersions() {
        // Reference data shared with every other class in this JVM — do not leak the fixture.
        CacheManager.supportedVersions.invalidateAll()
    }

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
        // there are no items, recipes or loot tables behind them. 1.19.4 is a real release this
        // instance may well have ingested — what makes it invalid here is that it is not in
        // [ingested], which is the question the step actually asks.
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
