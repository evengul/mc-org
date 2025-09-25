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

    var testHost: String? = "http://localhost:8080"

    var modrinthBaseUrl: String = "https://api.modrinth.com/v2"
    var microsoftLoginBaseUrl: String = "https://login.microsoftonline.com"
    var xboxAuthBaseUrl: String = "https://user.auth.xboxlive.com"
    var xstsAuthBaseUrl: String = "https://xsts.auth.xboxlive.com"
    var minecraftBaseUrl: String = "https://api.minecraftservices.com"

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
            if (env != Test) {
                if (it.isNullOrBlank()) {
                    errors.add("APP_HOST is not set")
                } else {
                    testHost = it
                }
            } else if (!it.isNullOrBlank()) {
                errors.add("APP_HOST is set in a non-TEST environment, should not be set")
            }
        }

        System.getenv("MODRINTH_BASE_URL")?.let { modrinthBaseUrl = it }
        System.getenv("MICROSOFT_LOGIN_BASE_URL")?.let { microsoftLoginBaseUrl = it }
        System.getenv("XBOX_AUTH_BASE_URL")?.let { xboxAuthBaseUrl = it }
        System.getenv("XSTS_AUTH_BASE_URL")?.let { xstsAuthBaseUrl = it }
        System.getenv("MINECRAFT_BASE_URL")?.let { minecraftBaseUrl = it }

        if (errors.isNotEmpty()) {
            logger.error("Invalid configuration:\n${errors.joinToString("\n")}")
        }
    }
}