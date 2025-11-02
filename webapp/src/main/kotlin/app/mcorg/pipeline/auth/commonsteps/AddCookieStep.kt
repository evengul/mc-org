package app.mcorg.pipeline.auth.commonsteps

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.presentation.consts.AUTH_COOKIE
import io.ktor.server.response.ResponseCookies
import io.ktor.util.date.GMTDate
import java.time.Instant

data object AddCookieFailure

data class AddCookieStep(val cookies: ResponseCookies, val host: String) : Step<String, AddCookieFailure, Unit> {
    override suspend fun process(input: String): Result<AddCookieFailure, Unit> {
        val expires = GMTDate(timestamp = Instant.now().plusSeconds(8 * 60 * 60).toEpochMilli())
        if (host == "false") {
            cookies.append(AUTH_COOKIE, input, httpOnly = true, expires = expires, path = "/")
        } else {
            cookies.append(AUTH_COOKIE, input, httpOnly = true, expires = expires, path = "/", domain = host)
        }
        return Result.success(Unit)
    }
}
