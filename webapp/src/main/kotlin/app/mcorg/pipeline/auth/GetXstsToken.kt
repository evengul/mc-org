package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.auth.minecraft.XboxTokenResponse
import app.mcorg.pipeline.auth.minecraft.XstsProperties
import app.mcorg.pipeline.auth.minecraft.XstsRequest
import app.mcorg.pipeline.failure.GetXstsTokenFailure
import app.mcorg.pipeline.failure.ApiFailure
import app.mcorg.config.XstsAuthorizationApiConfig
import io.ktor.client.request.setBody

object GetXstsToken : Step<TokenData, GetXstsTokenFailure, TokenData> {
    override suspend fun process(input: TokenData): Result<GetXstsTokenFailure, TokenData> {
        val step = XstsAuthorizationApiConfig.getProvider()
            .post<TokenData, GetXstsTokenFailure, XboxTokenResponse>(
                url = XstsAuthorizationApiConfig.getAuthorizeUrl(),
                bodyBuilder = { requestBuilder, tokenData ->
                    val body = XstsRequest(
                        XstsProperties("RETAIL", listOf(tokenData.token)),
                        "rp://api.minecraftservices.com/",
                        "JWT"
                    )
                    requestBuilder.setBody(body)
                },
                errorMapper = { apiFailure ->
                    when (apiFailure) {
                        is ApiFailure.HttpError -> GetXstsTokenFailure.CouldNotGetXstsToken
                        else -> GetXstsTokenFailure.CouldNotGetXstsToken
                    }
                }
            )

        return step.process(input).map { response ->
            TokenData(response.token, input.hash)
        }
    }
}