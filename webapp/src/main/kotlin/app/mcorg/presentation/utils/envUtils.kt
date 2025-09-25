package app.mcorg.presentation.utils

import app.mcorg.config.AppConfig
import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.domain.Test
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.request.host
import io.ktor.util.*

fun ApplicationCall.getHost(): String? {
    val env = AppConfig.env
    val referrer = request.headers[HttpHeaders.Referrer] ?: request.host()
    return when (env) {
        Production -> return if (referrer.contains("mcorg.fly.dev")) "mcorg.fly.dev"
                        else "mcorg.app"
        Test -> AppConfig.testHost ?: "http://localhost:8080"
        Local -> null
        else -> throw IllegalStateException("Invalid ENV=[$env]")
    }
}