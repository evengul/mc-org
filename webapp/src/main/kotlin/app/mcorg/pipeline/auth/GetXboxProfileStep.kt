package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.auth.minecraft.XboxProfileRequest
import app.mcorg.pipeline.auth.minecraft.XboxProperties
import app.mcorg.pipeline.auth.minecraft.XboxTokenResponse
import app.mcorg.pipeline.failure.GetXboxProfileFailure
import app.mcorg.pipeline.failure.ApiFailure
import app.mcorg.pipeline.ApiSteps
import app.mcorg.config.XboxAuthApiProvider
import io.ktor.client.request.setBody
import org.slf4j.LoggerFactory

object GetXboxProfileStep : Step<String, GetXboxProfileFailure, TokenData> {

    private val logger = LoggerFactory.getLogger(GetXboxProfileStep::class.java)

    override suspend fun process(input: String): Result<GetXboxProfileFailure, TokenData> {
        val step = ApiSteps.postJson<String, GetXboxProfileFailure, XboxTokenResponse>(
            apiProvider = XboxAuthApiProvider,
            url = XboxAuthApiProvider.getAuthenticateUrl(),
            bodyBuilder = { requestBuilder, accessToken ->
                val body = XboxProfileRequest(
                    XboxProperties("RPS", "user.auth.xboxlive.com", "d=$accessToken"),
                    "http://auth.xboxlive.com",
                    "JWT"
                )
                requestBuilder.setBody(body)
            },
            errorMapper = { apiFailure ->
                when (apiFailure) {
                    is ApiFailure.HttpError -> {
                        logger.error("Could not get Xbox profile: HTTP ${apiFailure.statusCode}")
                        GetXboxProfileFailure.CouldNotGetXboxProfile
                    }
                    else -> {
                        logger.error("Could not get Xbox profile: $apiFailure")
                        GetXboxProfileFailure.CouldNotGetXboxProfile
                    }
                }
            }
        )

        return step.process(input).map { response ->
            TokenData(response.token, response.userHash())
        }
    }
}