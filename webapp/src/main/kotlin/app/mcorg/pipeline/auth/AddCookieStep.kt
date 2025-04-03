package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.presentation.utils.tokenName
import io.ktor.server.response.ResponseCookies
import io.ktor.util.date.GMTDate
import java.time.Instant

sealed interface AddCookieFailure : SignInLocallyFailure, SignInWithMinecraftFailure

data class AddCookieStep(val cookies: ResponseCookies, val host: String) : Step<String, AddCookieFailure, Unit> {
    override fun process(input: String): Result<AddCookieFailure, Unit> {
        val expires = GMTDate(timestamp = Instant.now().plusSeconds(8 * 60 * 60).toEpochMilli())
        if (host == "false") {
            cookies.append(tokenName, input, httpOnly = true, expires = expires, path = "/")
        } else {
            cookies.append(tokenName, input, httpOnly = true, expires = expires, path = "/", domain = host)
        }
        return Result.success(Unit)
    }
}
