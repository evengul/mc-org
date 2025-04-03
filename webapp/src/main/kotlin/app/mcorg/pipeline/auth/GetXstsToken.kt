package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.infrastructure.gateway.XboxTokenResponse
import app.mcorg.infrastructure.gateway.XstsProperties
import app.mcorg.infrastructure.gateway.XstsRequest
import app.mcorg.pipeline.apiPostJson
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking

sealed interface GetXstsTokenFailure : AuthPluginFailure {
    data object CouldNotGetXstsToken : GetXstsTokenFailure
}

object GetXstsToken : Step<TokenData, GetXstsTokenFailure, TokenData> {
    override fun process(input: TokenData): Result<GetXstsTokenFailure, TokenData> {
        return runBlocking {
            val body = XstsRequest(XstsProperties("RETAIL", listOf(input.token)), "rp://api.minecraftservices.com/", "JWT")
            val result = apiPostJson("https://xsts.auth.xboxlive.com/xsts/authorize", body)
            if (result.status.isSuccess()) {
                val resultBody = result.body<XboxTokenResponse>()
                return@runBlocking Result.success(TokenData(resultBody.token, input.hash))
            } else {
                return@runBlocking Result.failure(GetXstsTokenFailure.CouldNotGetXstsToken)
            }
        }
    }
}