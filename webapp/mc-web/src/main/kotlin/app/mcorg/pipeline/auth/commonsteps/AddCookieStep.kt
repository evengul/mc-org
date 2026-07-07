package app.mcorg.pipeline.auth.commonsteps

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.presentation.consts.AUTH_COOKIE
import io.ktor.server.response.*
import io.ktor.util.date.*
import java.time.Instant

data class AddCookieStep(val cookies: ResponseCookies, val host: String) : Step<String, Nothing, Unit> {
    override suspend fun process(input: String): Result<Nothing, Unit> {
        val expires = GMTDate(timestamp = Instant.now().plusSeconds(8 * 60 * 60).toEpochMilli())
        // SameSite=Lax blunts CSRF on state-changing POSTs (e.g. /link) while keeping top-level
        // navigations authenticated. Secure only in Production so http://localhost dev still works.
        val secure = AppConfig.env == Production
        val sameSite = mapOf("SameSite" to "Lax")
        if (host == "false") {
            cookies.append(AUTH_COOKIE, input, httpOnly = true, expires = expires, path = "/", secure = secure, extensions = sameSite)
        } else {
            cookies.append(AUTH_COOKIE, input, httpOnly = true, expires = expires, path = "/", domain = host, secure = secure, extensions = sameSite)
        }
        return Result.success(Unit)
    }
}
