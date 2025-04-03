package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.infrastructure.gateway.XboxProfileRequest
import app.mcorg.infrastructure.gateway.XboxProperties
import app.mcorg.infrastructure.gateway.XboxTokenResponse
import app.mcorg.pipeline.apiPostJson
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

sealed interface GetXboxProfileFailure : SignInWithMinecraftFailure {
    object CouldNotGetXboxProfile : GetXboxProfileFailure
}

object GetXboxProfileStep : Step<String, GetXboxProfileFailure, TokenData> {

    private val logger = LoggerFactory.getLogger(GetXboxProfileStep::class.java)

    override fun process(input: String): Result<GetXboxProfileFailure, TokenData> {
        return runBlocking {
            val body = XboxProfileRequest(XboxProperties("RPS", "user.auth.xboxlive.com", "d=$input"), "http://auth.xboxlive.com", "JWT")
            val result = apiPostJson("https://user.auth.xboxlive.com/user/authenticate", body)
            if (result.status.isSuccess()) {
                val resultBody = result.body<XboxTokenResponse>()
                return@runBlocking Result.success(TokenData(resultBody.token, resultBody.userHash()))
            } else {
                logger.error("Could not get Xbox profile: ${result.status} ${result.bodyAsText()}")
                return@runBlocking Result.failure(GetXboxProfileFailure.CouldNotGetXboxProfile)
            }
        }
    }
}