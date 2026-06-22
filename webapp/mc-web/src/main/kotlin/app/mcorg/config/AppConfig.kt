package app.mcorg.config

import app.mcorg.domain.Env
import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.domain.Test
import org.slf4j.LoggerFactory

object AppConfig {
    private val logger = LoggerFactory.getLogger(AppConfig::class.java)

    var dbUrl: String = "jdbc:postgresql://localhost:5432/postgres"
    var dbUsername: String = "postgres"
    var dbPassword: String = "supersecret"

    var env: Env = Local

    var microsoftClientId: String = ""
    var microsoftClientSecret: String = ""

    var skipMicrosoftSignIn: Boolean = false

    var rsaPrivateKey: String? = null
    var rsaPublicKey: String? = null

    // Public host for the current environment. Drives the OAuth redirect_uri and auth-cookie domain.
    // Production defaults to "app.seam.gg"; override via APP_HOST. Required in TEST, unused in LOCAL.
    var appHost: String? = null

    var modrinthBaseUrl: String = "https://api.modrinth.com/v2"
    var microsoftLoginBaseUrl: String = "https://login.microsoftonline.com"
    var xboxAuthBaseUrl: String = "https://user.auth.xboxlive.com"
    var xstsAuthBaseUrl: String = "https://xsts.auth.xboxlive.com"
    var minecraftBaseUrl: String = "https://api.minecraftservices.com"
    var launcherMetaBaseUrl: String = "https://launchermeta.mojang.com"

    var demoUser: String = "evegul"

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

    init {
        val errors = mutableListOf<String>()
        System.getenv("DB_URL")?.let { dbUrl = it } ?: errors.add("DB_URL is not set")
        System.getenv("DB_USER")?.let { dbUsername = it } ?: errors.add("DB_USER is not set")
        System.getenv("DB_PASSWORD")?.let { dbPassword = it } ?: errors.add("DB_PASSWORD is not set")

        System.getenv("ENV")?.let {
            when(it) {
                "LOCAL" -> Local
                "TEST" -> Test
                "PRODUCTION" -> Production
                else -> {
                    errors.add("ENV must be one of LOCAL, TEST, PRODUCTION")
                    null
                }
            }
        }?.let { env = it }

        System.getenv("SKIP_MICROSOFT_SIGN_IN")?.let { skipMicrosoftSignIn = it.toBoolean() }

        System.getenv("MICROSOFT_CLIENT_ID").let {
            if (!skipMicrosoftSignIn) {
                if (it.isNullOrBlank()) {
                    errors.add("MICROSOFT_CLIENT_ID is not set")
                } else {
                    microsoftClientId = it
                }
            }
        }

        System.getenv("MICROSOFT_CLIENT_SECRET").let {
            if (!skipMicrosoftSignIn) {
                if (it.isNullOrBlank()) {
                    errors.add("MICROSOFT_CLIENT_SECRET is not set")
                } else {
                    microsoftClientSecret = it
                }
            }
        }

        System.getenv("RSA_PRIVATE_KEY").let {
            if (env == Local) {
                errors.add("RSA_PRIVATE_KEY must be set in LOCAL environment, should use generated key")
            } else if (it.isNullOrBlank()) {
                errors.add("RSA_PRIVATE_KEY is not set")
            } else {
                rsaPrivateKey = it
            }
        }

        System.getenv("RSA_PUBLIC_KEY").let {
            if (env == Local) {
                errors.add("RSA_PUBLIC_KEY must be set in LOCAL environment, should use generated key")
            } else if (it.isNullOrBlank()) {
                errors.add("RSA_PUBLIC_KEY is not set")
            } else {
                rsaPublicKey = it
            }
        }

        System.getenv("APP_HOST").let {
            when (env) {
                Production -> appHost = if (it.isNullOrBlank()) "app.seam.gg" else it
                Test -> {
                    if (it.isNullOrBlank()) errors.add("APP_HOST is not set") else appHost = it
                }
                Local -> { /* host is not used in LOCAL — getHost() returns null */ }
            }
        }

        System.getenv("MODRINTH_BASE_URL")?.let { modrinthBaseUrl = it }
        System.getenv("MICROSOFT_LOGIN_BASE_URL")?.let { microsoftLoginBaseUrl = it }
        System.getenv("XBOX_AUTH_BASE_URL")?.let { xboxAuthBaseUrl = it }
        System.getenv("XSTS_AUTH_BASE_URL")?.let { xstsAuthBaseUrl = it }
        System.getenv("MINECRAFT_BASE_URL")?.let { minecraftBaseUrl = it }
        System.getenv("LAUNCHER_META_BASE_URL")?.let { launcherMetaBaseUrl = it }

        System.getenv("DEMO_USER")?.let { demoUser = it }

        System.getenv("WEBHOOK_ADMIN_SECRET")?.let { webhookAdminSecret = it }

        System.getenv("SEAM_DISCORD_URL")?.let { seamDiscordUrl = it }
        System.getenv("SEAM_WEBHOOK_SHARED_SECRET")?.let { webhookSharedSecret = it }

        System.getenv("PREVIEW_PASSWORD")?.let { previewPassword = it }
        if (env == Test && previewPassword.isNullOrBlank()) {
            errors.add("PREVIEW_PASSWORD is not set (required in TEST to gate the public preview)")
        }

        if (errors.isNotEmpty()) {
            logger.error("Invalid configuration:\n${errors.joinToString("\n")}")
        }
    }
}