package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.infrastructure.gateway.XboxTokenResponse
import app.mcorg.infrastructure.gateway.XstsProperties
import app.mcorg.infrastructure.gateway.XstsRequest
import app.mcorg.pipeline.apiPostJson
import io.ktor.client.call.body
import io.ktor.http.isSuccess

sealed interface GetXstsTokenFailure : SignInWithMinecraftFailure {
    data object CouldNotGetXstsToken : GetXstsTokenFailure
}

object GetXstsToken : Step<TokenData, GetXstsTokenFailure, TokenData> {
    override suspend fun process(input: TokenData): Result<GetXstsTokenFailure, TokenData> {
        val body = XstsRequest(XstsProperties("RETAIL", listOf(input.token)), "rp://api.minecraftservices.com/", "JWT")
        val result = apiPostJson("https://xsts.auth.xboxlive.com/xsts/authorize", body)
        if (result.status.isSuccess()) {
            val resultBody = result.body<XboxTokenResponse>()
            return Result.success(TokenData(resultBody.token, input.hash))
        } else {
            return Result.failure(GetXstsTokenFailure.CouldNotGetXstsToken)
        }
    }
}