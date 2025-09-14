package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.auth.minecraft.MinecraftRequest
import app.mcorg.pipeline.auth.minecraft.MinecraftTokenResponse
import app.mcorg.pipeline.auth.minecraft.createMinecraftRequest
import app.mcorg.pipeline.failure.GetMinecraftTokenFailure
import app.mcorg.pipeline.failure.ApiFailure
import app.mcorg.config.MinecraftApiConfig
import io.ktor.client.request.setBody

object GetMinecraftToken : Step<TokenData, GetMinecraftTokenFailure, String> {
    override suspend fun process(input: TokenData): Result<GetMinecraftTokenFailure, String> {
        val step = MinecraftApiConfig.getProvider()
            .post<TokenData, GetMinecraftTokenFailure, MinecraftTokenResponse>(
                url = MinecraftApiConfig.getAuthenticateUrl(),
                bodyBuilder = { requestBuilder, tokenData ->
                    val body = MinecraftRequest(createMinecraftRequest(userHash = tokenData.hash, xstsToken = tokenData.token))
                    requestBuilder.setBody(body)
                },
                errorMapper = { apiFailure ->
                    when (apiFailure) {
                        is ApiFailure.HttpError -> GetMinecraftTokenFailure.CouldNotGetMinecraftToken
                        else -> GetMinecraftTokenFailure.CouldNotGetMinecraftToken
                    }
                }
            )

        return step.process(input).map { response -> response.accessToken }
    }
}