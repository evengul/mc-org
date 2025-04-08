package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.infrastructure.gateway.MinecraftRequest
import app.mcorg.infrastructure.gateway.MinecraftTokenResponse
import app.mcorg.infrastructure.gateway.createMinecraftRequest
import app.mcorg.pipeline.apiPostJson
import io.ktor.client.call.body
import io.ktor.http.isSuccess

sealed interface GetMinecraftTokenFailure : SignInWithMinecraftFailure {
    data object CouldNotGetMinecraftToken : GetMinecraftTokenFailure
}

object GetMinecraftToken : Step<TokenData, GetMinecraftTokenFailure, String> {
    override suspend fun process(input: TokenData): Result<GetMinecraftTokenFailure, String> {
        val url = "https://api.minecraftservices.com/authentication/login_with_xbox"
        val body = MinecraftRequest(createMinecraftRequest(userHash = input.hash, xstsToken = input.token))
        val response = apiPostJson(url, body)
        if (response.status.isSuccess()) {
            val token = response.body<MinecraftTokenResponse>().accessToken
            return Result.success(token)
        }
        return Result.failure(GetMinecraftTokenFailure.CouldNotGetMinecraftToken)
    }
}