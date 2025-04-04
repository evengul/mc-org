package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.infrastructure.gateway.MinecraftRequest
import app.mcorg.infrastructure.gateway.MinecraftTokenResponse
import app.mcorg.infrastructure.gateway.createMinecraftRequest
import app.mcorg.pipeline.apiPostJson
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking

sealed interface GetMinecraftTokenFailure : SignInWithMinecraftFailure {
    data object CouldNotGetMinecraftToken : GetMinecraftTokenFailure
}

object GetMinecraftToken : Step<TokenData, GetMinecraftTokenFailure, String> {
    override fun process(input: TokenData): Result<GetMinecraftTokenFailure, String> {
        return runBlocking {
            val url = "https://api.minecraftservices.com/authentication/login_with_xbox"
            val body = MinecraftRequest(createMinecraftRequest(userHash = input.hash, xstsToken = input.token))
            val response = apiPostJson(url, body)
            if (response.status.isSuccess()) {
                val token = response.body<MinecraftTokenResponse>().accessToken
                return@runBlocking Result.success(token)
            }
            return@runBlocking Result.failure(GetMinecraftTokenFailure.CouldNotGetMinecraftToken)
        }
    }
}