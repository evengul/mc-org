package app.mcorg.config

import app.mcorg.domain.Env
import app.mcorg.domain.Local
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * Holder for the resolved [Config] (MCO-332).
 *
 * The fields stay `var` deliberately: ~40 call sites read `AppConfig.x` statically, and the
 * integration tests mutate a handful of them per test class. Immutability lives in [Config];
 * this object is the mutable window onto it. Scoping the test mutation is MCO-379.
 *
 * Loading happens in [init] — best-effort, so a LOCAL developer with no env file still boots —
 * and [initOrExit] is what turns problems into a non-zero exit. Call it first thing in `main()`:
 * nothing else validates configuration, and before MCO-332 the error was emitted incidentally by
 * whichever code happened to touch this object first.
 */
object AppConfig {
    private val logger = LoggerFactory.getLogger(AppConfig::class.java)

    var dbUrl: String = ""
    var dbUsername: String = ""
    var dbPassword: String = ""

    var env: Env = Local

    var microsoftClientId: String = ""
    var microsoftClientSecret: String = ""

    var skipMicrosoftSignIn: Boolean = false

    var rsaPrivateKey: String? = null
    var rsaPublicKey: String? = null

    // Public host for the current environment. Drives the OAuth redirect_uri and auth-cookie domain.
    // Production defaults to "app.seam.gg"; override via APP_HOST. Required in TEST, unused in LOCAL.
    var appHost: String? = null

    var microsoftLoginBaseUrl: String = ""
    var xboxAuthBaseUrl: String = ""
    var xstsAuthBaseUrl: String = ""
    var minecraftBaseUrl: String = ""
    var launcherMetaBaseUrl: String = ""

    // Username for the demo sign-in bypass. No default: when unset, demo sign-in fails closed.
    var demoUser: String? = null

    // Shared secret for the TEST preview's HTTP Basic Auth gate. Required in TEST (fails closed
    // if unset); unused in LOCAL and PRODUCTION. See PreviewGate.
    var previewPassword: String? = null

    // Shared secret gating the machine-facing webhook admin endpoints (MCO-229). Optional: when
    // unset the endpoints fail closed (reject every request), so the feature is inert until a
    // WEBHOOK_ADMIN_SECRET is provided. See WebhookAdminAuthPlugin.
    var webhookAdminSecret: String? = null

    // Base URL of the seam-discord Cloudflare Worker (MCO-240). World admins connect a Discord
    // channel from world settings; the resulting webhook callback URL is built from this base
    // (`<SEAM_DISCORD_URL>/seam-events/<channelId>`). Optional: when unset the Discord settings
    // section renders a "not configured" state instead of the connect form.
    var seamDiscordUrl: String? = null

    // Secret shared with the seam-discord bot (its `SEAM_WEBHOOK_SECRET`), used to sign deliveries
    // to the Worker (MCO-240). Stored as the subscription's `secret`; never shown in the UI.
    // Optional: when unset the Discord settings section fails closed (not-configured state).
    var webhookSharedSecret: String? = null

    // Comma-separated Minecraft versions (or `all`) to re-ingest regardless of the ledger.
    // Read by GetServerFilesPipeline; storage is idempotent, so forcing is safe.
    var forceReingest: String? = null

    /** Problems found while reading the environment. Empty once configuration is valid. */
    private var fatalProblems: List<String> = emptyList()
    private var problems: List<String> = emptyList()

    init {
        load(readConfig())
    }

    internal fun load(result: ConfigLoad) {
        apply(result.config)
        fatalProblems = result.fatal
        problems = result.errors
    }

    internal fun apply(config: Config) {
        dbUrl = config.dbUrl
        dbUsername = config.dbUsername
        dbPassword = config.dbPassword
        env = config.env
        microsoftClientId = config.microsoftClientId
        microsoftClientSecret = config.microsoftClientSecret
        skipMicrosoftSignIn = config.skipMicrosoftSignIn
        rsaPrivateKey = config.rsaPrivateKey
        rsaPublicKey = config.rsaPublicKey
        appHost = config.appHost
        microsoftLoginBaseUrl = config.microsoftLoginBaseUrl
        xboxAuthBaseUrl = config.xboxAuthBaseUrl
        xstsAuthBaseUrl = config.xstsAuthBaseUrl
        minecraftBaseUrl = config.minecraftBaseUrl
        launcherMetaBaseUrl = config.launcherMetaBaseUrl
        demoUser = config.demoUser
        previewPassword = config.previewPassword
        webhookAdminSecret = config.webhookAdminSecret
        seamDiscordUrl = config.seamDiscordUrl
        webhookSharedSecret = config.webhookSharedSecret
        forceReingest = config.forceReingest
    }

    /**
     * Validates the configuration read at startup and terminates the process if it is unusable.
     * Call as the first statement of `main()`, before anything binds a port or opens a pool.
     *
     * Exits on:
     * - any fatal problem, in every environment (an unparseable `ENV` — see [ConfigLoad])
     * - any other problem outside LOCAL
     *
     * In LOCAL, non-fatal problems are logged as warnings and the process continues, so running
     * without a sourced env file still works.
     */
    fun initOrExit() {
        if (fatalProblems.isNotEmpty()) {
            logger.error("Invalid configuration:\n${fatalProblems.joinToString("\n") { "  - $it" }}")
            exitProcess(1)
        }
        if (problems.isEmpty()) return

        if (env == Local) {
            logger.warn(
                "Incomplete configuration (continuing because ENV is LOCAL):\n" +
                    problems.joinToString("\n") { "  - $it" }
            )
        } else {
            logger.error("Invalid configuration:\n${problems.joinToString("\n") { "  - $it" }}")
            exitProcess(1)
        }
    }
}
