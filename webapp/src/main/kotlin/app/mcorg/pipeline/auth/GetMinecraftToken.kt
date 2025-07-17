package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.auth.minecraft.MinecraftRequest
import app.mcorg.pipeline.auth.minecraft.MinecraftTokenResponse
import app.mcorg.pipeline.auth.minecraft.createMinecraftRequest
import app.mcorg.pipeline.apiPostJson
import app.mcorg.pipeline.failure.GetMinecraftTokenFailure
import io.ktor.client.call.body
import io.ktor.http.isSuccess

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