package app.mcorg.config

import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.domain.Test
import app.mcorg.pipeline.Result
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test as JUnitTest

/**
 * Unit tests for the pure configuration loader (MCO-331, MCO-332).
 *
 * Everything goes through a fake `getenv`, so no test touches the real process environment.
 */
class ConfigLoaderTest {

    private fun envOf(vararg pairs: Pair<String, String>): (String) -> String? {
        val map = pairs.toMap()
        return { map[it] }
    }

    /** A complete, valid PRODUCTION environment — the baseline the negative cases subtract from. */
    private val productionEnv = arrayOf(
        "ENV" to "PRODUCTION",
        "DB_URL" to "jdbc:postgresql://db.example.com:5432/mcorg",
        "DB_USER" to "mcorg",
        "DB_PASSWORD" to "hunter2",
        "MICROSOFT_CLIENT_ID" to "client-id",
        "MICROSOFT_CLIENT_SECRET" to "client-secret",
        "RSA_PRIVATE_KEY" to "private",
        "RSA_PUBLIC_KEY" to "public",
    )

    private fun productionEnvWithout(vararg names: String) =
        envOf(*productionEnv.filterNot { it.first in names }.toTypedArray())

    @Nested
    inner class EnvParsing {

        @JUnitTest
        fun `an unrecognised ENV never yields Local`() {
            // MCO-331: the single riskiest line in the old file. `Local` is the maximally
            // permissive branch, so a typo must not resolve to it.
            for (typo in listOf("PROD", "production", "Production", "prod", "STAGING", "  ")) {
                val load = readConfig(envOf("ENV" to typo, *productionEnv.drop(1).toTypedArray()))
                if (typo.isBlank()) {
                    // Blank is treated as absent — the developer path, see below.
                    assertTrue(load.fatal.isEmpty(), "blank ENV should not be fatal")
                } else {
                    assertTrue(
                        load.fatal.any { it.startsWith("ENV must be one of") },
                        "ENV='$typo' should be a fatal problem, got ${load.fatal}",
                    )
                }
            }
        }

        @JUnitTest
        fun `an unrecognised ENV is fatal in every environment, not just outside Local`() {
            // The whole point: we cannot decide "is this Local?" from a value that did not parse.
            val load = readConfig(envOf("ENV" to "PROD"))
            assertTrue(load.fatal.isNotEmpty())
            assertContains(load.fatal.joinToString(), "ENV must be one of LOCAL, TEST, PRODUCTION")
        }

        @JUnitTest
        fun `the fatal ENV message quotes the offending value`() {
            val load = readConfig(envOf("ENV" to "PROD"))
            assertContains(load.fatal.joinToString(), "'PROD'")
        }

        @JUnitTest
        fun `an absent ENV means Local`() {
            // The documented developer path: local.env genuinely omits ENV.
            val load = readConfig(envOf())
            assertEquals(Local, load.config.env)
            assertTrue(load.fatal.isEmpty())
        }

        @JUnitTest
        fun `surrounding whitespace on a valid ENV is tolerated`() {
            assertEquals(Production, readConfig(envOf("ENV" to " PRODUCTION ")).config.env)
        }

        @JUnitTest
        fun `each recognised ENV maps to its own value`() {
            assertEquals(Local, readConfig(envOf("ENV" to "LOCAL")).config.env)
            assertEquals(Test, readConfig(envOf("ENV" to "TEST")).config.env)
            assertEquals(Production, readConfig(envOf("ENV" to "PRODUCTION")).config.env)
        }
    }

    @Nested
    inner class RequiredValues {

        @JUnitTest
        fun `a complete production environment loads cleanly`() {
            val load = readConfig(envOf(*productionEnv))
            assertFalse(load.hasProblems, "unexpected problems: ${load.all}")
            assertEquals(Production, load.config.env)
            assertEquals("app.seam.gg", load.config.appHost)
        }

        @JUnitTest
        fun `a missing RSA private key in production is reported by name`() {
            val load = readConfig(productionEnvWithout("RSA_PRIVATE_KEY"))
            assertContains(load.errors, "RSA_PRIVATE_KEY is not set")
        }

        @JUnitTest
        fun `missing database credentials are each reported`() {
            val load = readConfig(productionEnvWithout("DB_URL", "DB_USER", "DB_PASSWORD"))
            assertContains(load.errors, "DB_URL is not set")
            assertContains(load.errors, "DB_USER is not set")
            assertContains(load.errors, "DB_PASSWORD is not set")
        }

        @JUnitTest
        fun `problems accumulate rather than short-circuiting on the first one`() {
            val load = readConfig(envOf("ENV" to "PRODUCTION"))
            // DB x3, Microsoft x2, RSA x2
            assertEquals(7, load.errors.size, "got ${load.errors}")
        }

        @JUnitTest
        fun `Local tolerates an entirely empty environment`() {
            // AC: `ENV=LOCAL` with an empty environment still starts. Problems are still
            // *recorded* — initOrExit downgrades them to warnings — but nothing is fatal.
            val load = readConfig(envOf())
            assertEquals(Local, load.config.env)
            assertTrue(load.fatal.isEmpty())
        }

        @JUnitTest
        fun `Local does not require the RSA pair`() {
            // jwt.kt falls back to the generated PEM pair locally.
            val load = readConfig(envOf("ENV" to "LOCAL"))
            assertFalse(load.errors.any { it.startsWith("RSA_") }, "got ${load.errors}")
            assertNull(load.config.rsaPrivateKey)
        }

        @JUnitTest
        fun `skipping Microsoft sign-in drops the credential requirement`() {
            val load = readConfig(
                envOf(
                    "ENV" to "LOCAL",
                    "SKIP_MICROSOFT_SIGN_IN" to "true",
                )
            )
            assertFalse(load.errors.any { it.startsWith("MICROSOFT_") }, "got ${load.errors}")
        }

        @JUnitTest
        fun `not skipping Microsoft sign-in requires both credentials`() {
            val load = readConfig(envOf("ENV" to "LOCAL", "SKIP_MICROSOFT_SIGN_IN" to "false"))
            assertContains(load.errors, "MICROSOFT_CLIENT_ID is not set")
            assertContains(load.errors, "MICROSOFT_CLIENT_SECRET is not set")
        }

        @JUnitTest
        fun `a blank required value counts as unset`() {
            val load = readConfig(envOf(*productionEnv, "RSA_PRIVATE_KEY" to "   "))
            assertContains(load.errors, "RSA_PRIVATE_KEY is not set")
        }
    }

    @Nested
    inner class AppHost {

        @JUnitTest
        fun `Production defaults to app seam gg`() {
            assertEquals("app.seam.gg", readConfig(envOf(*productionEnv)).config.appHost)
        }

        @JUnitTest
        fun `Production honours an explicit APP_HOST`() {
            val load = readConfig(envOf(*productionEnv, "APP_HOST" to "beta.seam.gg"))
            assertEquals("beta.seam.gg", load.config.appHost)
        }

        @JUnitTest
        fun `Test requires APP_HOST`() {
            val load = readConfig(envOf("ENV" to "TEST"))
            assertContains(load.errors, "APP_HOST is not set")
        }

        @JUnitTest
        fun `Local leaves APP_HOST unused`() {
            assertNull(readConfig(envOf("ENV" to "LOCAL", "APP_HOST" to "ignored")).config.appHost)
        }
    }

    @Nested
    inner class Port {

        @JUnitTest
        fun `defaults to 8080 when PORT is unset`() {
            assertEquals(8080, readConfig(envOf("ENV" to "LOCAL")).config.port)
        }

        @JUnitTest
        fun `defaults to 8080 when PORT is blank`() {
            val load = readConfig(envOf("ENV" to "LOCAL", "PORT" to "  "))
            assertEquals(8080, load.config.port)
            assertTrue(load.errors.none { it.startsWith("PORT") }, "got ${load.errors}")
        }

        @JUnitTest
        fun `honours an explicit PORT`() {
            assertEquals(8093, readConfig(envOf("ENV" to "LOCAL", "PORT" to "8093")).config.port)
        }

        @JUnitTest
        fun `tolerates surrounding whitespace`() {
            assertEquals(8093, readConfig(envOf("ENV" to "LOCAL", "PORT" to " 8093 ")).config.port)
        }

        @JUnitTest
        fun `rejects a non-numeric PORT`() {
            val load = readConfig(envOf("ENV" to "LOCAL", "PORT" to "eighty-eighty"))
            assertContains(load.errors, "PORT must be a number between 1 and 65535 (got 'eighty-eighty')")
            assertEquals(8080, load.config.port)
        }

        @JUnitTest
        fun `rejects a PORT outside the valid range`() {
            // 70000 parses as an Int but is not a port — the range check is what catches it, and
            // without it Netty fails much later with an opaque bind error.
            val load = readConfig(envOf("ENV" to "LOCAL", "PORT" to "70000"))
            assertContains(load.errors, "PORT must be a number between 1 and 65535 (got '70000')")
        }

        @JUnitTest
        fun `a bad PORT is fatal outside LOCAL`() {
            // Not in `fatal` (that bucket is ENV alone), but in `errors`, which initOrExit treats
            // as fatal everywhere except LOCAL.
            val load = readConfig(envOf(*productionEnv, "PORT" to "0"))
            assertTrue(load.errors.any { it.startsWith("PORT") }, "got ${load.errors}")
        }
    }

    @Nested
    inner class PreviewPassword {

        @JUnitTest
        fun `Test requires PREVIEW_PASSWORD`() {
            val load = readConfig(envOf("ENV" to "TEST"))
            assertTrue(load.errors.any { it.startsWith("PREVIEW_PASSWORD is not set") }, "got ${load.errors}")
        }

        @JUnitTest
        fun `Production does not require PREVIEW_PASSWORD`() {
            val load = readConfig(envOf(*productionEnv))
            assertFalse(load.errors.any { it.startsWith("PREVIEW_PASSWORD") })
        }
    }

    @Nested
    inner class OptionalValues {

        @JUnitTest
        fun `base urls fall back to their documented defaults`() {
            val config = readConfig(envOf("ENV" to "LOCAL")).config
            assertEquals("https://login.microsoftonline.com", config.microsoftLoginBaseUrl)
            assertEquals("https://user.auth.xboxlive.com", config.xboxAuthBaseUrl)
            assertEquals("https://xsts.auth.xboxlive.com", config.xstsAuthBaseUrl)
            assertEquals("https://api.minecraftservices.com", config.minecraftBaseUrl)
            assertEquals("https://launchermeta.mojang.com", config.launcherMetaBaseUrl)
        }

        @JUnitTest
        fun `base urls are overridable`() {
            val config = readConfig(envOf("ENV" to "LOCAL", "MINECRAFT_BASE_URL" to "http://localhost:9000")).config
            assertEquals("http://localhost:9000", config.minecraftBaseUrl)
        }

        @JUnitTest
        fun `optional secrets stay null when unset, so their features fail closed`() {
            val config = readConfig(envOf("ENV" to "LOCAL")).config
            assertNull(config.webhookAdminSecret)
            assertNull(config.seamDiscordUrl)
            assertNull(config.webhookSharedSecret)
        }

        @JUnitTest
        fun `FORCE_REINGEST is read through the loader, not System getenv`() {
            // MCO-332: the last stray System.getenv outside this file lived in
            // GetServerFilesPipeline.
            assertEquals("all", readConfig(envOf("ENV" to "LOCAL", "FORCE_REINGEST" to "all")).config.forceReingest)
            assertNull(readConfig(envOf("ENV" to "LOCAL")).config.forceReingest)
        }
    }

    @Nested
    inner class StrictLoad {

        @JUnitTest
        fun `loadConfig succeeds on a complete environment`() {
            val result = loadConfig(envOf(*productionEnv))
            assertTrue(result is Result.Success, "got $result")
            assertEquals(Production, result.value.env)
        }

        @JUnitTest
        fun `loadConfig fails with every problem listed`() {
            val result = loadConfig(productionEnvWithout("RSA_PUBLIC_KEY", "DB_USER"))
            assertTrue(result is Result.Failure, "got $result")
            assertContains(result.error, "RSA_PUBLIC_KEY is not set")
            assertContains(result.error, "DB_USER is not set")
        }

        @JUnitTest
        fun `loadConfig reports a fatal ENV even when nothing else is wrong`() {
            val result = loadConfig(envOf(*productionEnv, "ENV" to "PROD"))
            assertTrue(result is Result.Failure, "got $result")
            assertContains(result.error.joinToString(), "ENV must be one of")
        }

        @JUnitTest
        fun `getOrThrow on a failed load throws rather than returning defaults`() {
            assertThrows<IllegalStateException> { loadConfig(envOf("ENV" to "PRODUCTION")).getOrThrow() }
        }
    }
}
