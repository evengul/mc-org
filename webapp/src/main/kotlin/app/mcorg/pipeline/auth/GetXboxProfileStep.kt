package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.auth.minecraft.XboxProfileRequest
import app.mcorg.pipeline.auth.minecraft.XboxProperties
import app.mcorg.pipeline.auth.minecraft.XboxTokenResponse
import app.mcorg.pipeline.apiPostJson
import app.mcorg.pipeline.failure.GetXboxProfileFailure
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory

object GetXboxProfileStep : Step<String, GetXboxProfileFailure, TokenData> {

    private val logger = LoggerFactory.getLogger(GetXboxProfileStep::class.java)

    override suspend fun process(input: String): Result<GetXboxProfileFailure, TokenData> {
        val body = XboxProfileRequest(XboxProperties("RPS", "user.auth.xboxlive.com", "d=$input"), "http://auth.xboxlive.com", "JWT")
        val result = apiPostJson("https://user.auth.xboxlive.com/user/authenticate", body)
        if (result.status.isSuccess()) {
            val resultBody = result.body<XboxTokenResponse>()
            return Result.success(TokenData(resultBody.token, resultBody.userHash()))
        } else {
            logger.error("Could not get Xbox profile: ${result.status} ${result.bodyAsText()}")
            return Result.failure(GetXboxProfileFailure.CouldNotGetXboxProfile)
        }
    }
}