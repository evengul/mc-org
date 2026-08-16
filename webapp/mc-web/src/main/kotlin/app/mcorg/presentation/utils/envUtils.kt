package app.mcorg.presentation.utils

import app.mcorg.config.AppConfig
import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.domain.Test
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*

/**
 * The host to scope cookies and OAuth redirect URLs to.
 *
 * Reads `request.host()` — the Host header, which Fly's proxy sets from the hostname it routed —
 * rather than `Referer` (MCO-356). `Referer` is set by whatever page linked here, so it was
 * attacker-influenced input steering a security-relevant cookie attribute. For legitimate traffic
 * the two agreed anyway: arriving at `mcorg.fly.dev` with no `Referer` already fell through to
 * `request.host()`, so this narrows the input without changing the outcome.
 *
 * The `mcorg.fly.dev` branch exists because a Fly app is always reachable at its `.fly.dev` name
 * as well as at `APP_HOST`, and a cookie scoped to the wrong one of those is a cookie that is
 * never sent back.
 */
fun ApplicationCall.getHost(): String? {
    return when (AppConfig.env) {
        Production -> if (request.host().contains("mcorg.fly.dev")) "mcorg.fly.dev" else AppConfig.appHost
        Test -> AppConfig.appHost
        Local -> null
    }
}