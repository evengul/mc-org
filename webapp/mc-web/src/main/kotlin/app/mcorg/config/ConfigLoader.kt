package app.mcorg.config

import app.mcorg.domain.Env
import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.domain.Test
import app.mcorg.pipeline.Result

/** Port Netty binds when `PORT` is unset. Matches Dockerfile `EXPOSE` and fly.toml `internal_port`. */
const val DEFAULT_PORT = 8080

/**
 * The application's configuration, resolved once from the environment (MCO-332).
 *
 * Immutable by construction: everything that reads configuration goes through [AppConfig], which
 * holds one of these. Building it is a pure function of `getenv` ([readConfig]/[loadConfig]), so
 * every environment/variable combination is unit-testable without touching the real process
 * environment.
 */
data class Config(
    val dbUrl: String,
    val dbUsername: String,
    val dbPassword: String,
    val env: Env,
    val port: Int,
    val microsoftClientId: String,
    val microsoftClientSecret: String,
    val skipMicrosoftSignIn: Boolean,
    val rsaPrivateKey: String?,
    val rsaPublicKey: String?,
    val appHost: String?,
    val microsoftLoginBaseUrl: String,
    val xboxAuthBaseUrl: String,
    val xstsAuthBaseUrl: String,
    val minecraftBaseUrl: String,
    val launcherMetaBaseUrl: String,
    val demoUser: String?,
    val previewPassword: String?,
    val webhookAdminSecret: String?,
    val seamDiscordUrl: String?,
    val webhookSharedSecret: String?,
    val forceReingest: String?,
)

/**
 * The outcome of reading the environment: a best-effort [Config] plus whatever was wrong with it.
 *
 * Two error buckets, because they are not equally fatal:
 *
 * - [fatal] — wrong in *every* environment, including LOCAL. Only one thing qualifies: an `ENV`
 *   value that does not parse. Before MCO-331 that downgraded silently to [Local], and since every
 *   security control in the app is written as "is this Production?", LOCAL is the maximally
 *   permissive branch — a typo like `PROD` in `fly.toml` yielded a production deployment with
 *   insecure cookies, no HSTS, bypassed API bearer auth and demo sign-in enabled. There is no
 *   environment in which continuing is right, because the environment is precisely what is unknown.
 * - [errors] — fatal outside LOCAL. Missing secrets, hosts and credentials: boot-time-detectable,
 *   and previously turned into runtime mysteries (a missing `RSA_PRIVATE_KEY` booted fine and threw
 *   on the first sign-in attempt). LOCAL warns and continues so `test.sh` and `mvn exec:java` work
 *   without a sourced env file.
 */
internal data class ConfigLoad(
    val config: Config,
    val fatal: List<String>,
    val errors: List<String>,
) {
    val hasProblems: Boolean get() = fatal.isNotEmpty() || errors.isNotEmpty()
    val all: List<String> get() = fatal + errors
}

/**
 * Reads configuration from [getenv], returning the resolved [Config] or the accumulated problems.
 *
 * This is the strict form. [AppConfig] uses [readConfig] directly, because it needs the
 * best-effort config *and* the errors in order to warn-and-continue in LOCAL.
 */
internal fun loadConfig(getenv: (String) -> String? = System::getenv): Result<List<String>, Config> {
    val load = readConfig(getenv)
    return if (load.hasProblems) Result.Failure(load.all) else Result.Success(load.config)
}

/**
 * Pure environment read. Never throws, never logs, never exits — the caller decides what a problem
 * means. Order matters in two places: `ENV` is resolved first because the RSA and `APP_HOST` rules
 * depend on it, and `SKIP_MICROSOFT_SIGN_IN` before the Microsoft credentials for the same reason.
 */
internal fun readConfig(getenv: (String) -> String? = System::getenv): ConfigLoad {
    val fatal = mutableListOf<String>()
    val errors = mutableListOf<String>()

    fun required(name: String, default: String): String {
        val value = getenv(name)
        if (value.isNullOrBlank()) {
            errors.add("$name is not set")
            return default
        }
        return value
    }

    // ENV first: the rules below branch on it.
    //
    // Absent ENV means LOCAL — that is the documented developer path and `local.env` genuinely
    // omits it. Present-but-unparseable is fatal (MCO-331): we cannot pick a safe default for a
    // value whose whole job is to select the security posture.
    val rawEnv = getenv("ENV")
    val env: Env = when {
        rawEnv == null -> Local
        rawEnv.isBlank() -> Local
        else -> when (rawEnv.trim()) {
            "LOCAL" -> Local
            "TEST" -> Test
            "PRODUCTION" -> Production
            else -> {
                fatal.add("ENV must be one of LOCAL, TEST, PRODUCTION (got '$rawEnv')")
                Local
            }
        }
    }

    // The HTTP port Netty binds (MCO-476). Defaults to 8080 — what the Dockerfile's EXPOSE and both
    // fly.toml `internal_port` values assume, so production sets nothing. Worktrees set it so two
    // `run.sh` invocations can hold a server at once; see webapp/scripts/worktree-port.sh.
    //
    // An unparseable value is an error rather than a silent fall back to 8080: outside LOCAL that
    // would bind a port the platform is not routing to, which reads as "the deploy is down".
    val rawPort = getenv("PORT")
    val port: Int = if (rawPort.isNullOrBlank()) DEFAULT_PORT else {
        rawPort.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: run {
            errors.add("PORT must be a number between 1 and 65535 (got '$rawPort')")
            DEFAULT_PORT
        }
    }

    val dbUrl = required("DB_URL", "jdbc:postgresql://localhost:5432/postgres")
    val dbUsername = required("DB_USER", "postgres")
    val dbPassword = required("DB_PASSWORD", "supersecret")

    val skipMicrosoftSignIn = getenv("SKIP_MICROSOFT_SIGN_IN")?.toBoolean() ?: false

    var microsoftClientId = ""
    var microsoftClientSecret = ""
    if (!skipMicrosoftSignIn) {
        microsoftClientId = required("MICROSOFT_CLIENT_ID", "")
        microsoftClientSecret = required("MICROSOFT_CLIENT_SECRET", "")
    }

    // LOCAL deliberately leaves the RSA pair null: jwt.kt falls back to the generated PEM pair
    // (`mc-web/create-keys.sh` -> resources/keys), which is the intended local path.
    var rsaPrivateKey: String? = null
    var rsaPublicKey: String? = null
    if (env != Local) {
        rsaPrivateKey = required("RSA_PRIVATE_KEY", "").ifBlank { null }
        rsaPublicKey = required("RSA_PUBLIC_KEY", "").ifBlank { null }
    }

    val rawAppHost = getenv("APP_HOST")
    val appHost: String? = when (env) {
        Production -> if (rawAppHost.isNullOrBlank()) "app.seam.gg" else rawAppHost
        Test -> {
            if (rawAppHost.isNullOrBlank()) errors.add("APP_HOST is not set")
            rawAppHost
        }
        Local -> null // host is not used in LOCAL — getHost() returns null
    }

    val previewPassword = getenv("PREVIEW_PASSWORD")
    if (env == Test && previewPassword.isNullOrBlank()) {
        errors.add("PREVIEW_PASSWORD is not set (required in TEST to gate the public preview)")
    }

    fun optional(name: String, default: String): String = getenv(name)?.takeIf { it.isNotBlank() } ?: default

    return ConfigLoad(
        config = Config(
            dbUrl = dbUrl,
            dbUsername = dbUsername,
            dbPassword = dbPassword,
            env = env,
            port = port,
            microsoftClientId = microsoftClientId,
            microsoftClientSecret = microsoftClientSecret,
            skipMicrosoftSignIn = skipMicrosoftSignIn,
            rsaPrivateKey = rsaPrivateKey,
            rsaPublicKey = rsaPublicKey,
            appHost = appHost,
            microsoftLoginBaseUrl = optional("MICROSOFT_LOGIN_BASE_URL", "https://login.microsoftonline.com"),
            xboxAuthBaseUrl = optional("XBOX_AUTH_BASE_URL", "https://user.auth.xboxlive.com"),
            xstsAuthBaseUrl = optional("XSTS_AUTH_BASE_URL", "https://xsts.auth.xboxlive.com"),
            minecraftBaseUrl = optional("MINECRAFT_BASE_URL", "https://api.minecraftservices.com"),
            launcherMetaBaseUrl = optional("LAUNCHER_META_BASE_URL", "https://launchermeta.mojang.com"),
            // No default (MCO-333). This used to fall back to the literal "evegul" — a personal
            // username baked into every environment's defaults. Demo sign-in now fails closed
            // when it is unset, which is the right posture for a sign-in bypass anyway.
            demoUser = getenv("DEMO_USER")?.takeIf { it.isNotBlank() },
            previewPassword = previewPassword,
            webhookAdminSecret = getenv("WEBHOOK_ADMIN_SECRET"),
            seamDiscordUrl = getenv("SEAM_DISCORD_URL"),
            webhookSharedSecret = getenv("SEAM_WEBHOOK_SHARED_SECRET"),
            forceReingest = getenv("FORCE_REINGEST"),
        ),
        fatal = fatal,
        errors = errors,
    )
}
