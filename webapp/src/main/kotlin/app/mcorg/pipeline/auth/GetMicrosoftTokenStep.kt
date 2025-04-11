package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.auth.minecraft.MicrosoftAccessTokenErrorResponse
import app.mcorg.pipeline.auth.minecraft.MicrosoftAccessTokenResponse
import app.mcorg.domain.Env
import app.mcorg.domain.Local
import app.mcorg.pipeline.apiGetForm
import io.ktor.client.call.body
import io.ktor.http.isSuccess

sealed interface GetMicrosoftTokenFailure : SignInWithMinecraftFailure {
    data object NoHostForNonLocalEnv : GetMicrosoftTokenFailure
    data class CouldNotGetToken(val error: String, val description: String) : GetMicrosoftTokenFailure
}

data class GetMicrosoftTokenInput(
    val code: String,
    val clientId: String,
    val clientSecret: String,
    val env: Env,
    val host: String?
)

object GetMicrosoftTokenStep : Step<GetMicrosoftTokenInput, GetMicrosoftTokenFailure, String> {
    override suspend fun process(input: GetMicrosoftTokenInput): Result<GetMicrosoftTokenFailure, String> {
        val (code, clientId, clientSecret, env, host) = input
        val redirectUrl =
            if (env == Local) "http://localhost:8080/auth/oidc/microsoft-redirect"
            else if (host != null) "https://$host/auth/oidc/microsoft-redirect"
            else return Result.failure(GetMicrosoftTokenFailure.NoHostForNonLocalEnv)

        val apiUrl = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"

        val response = apiGetForm(apiUrl) {
            append("code", code)
            append("client_id", clientId)
            append("client_secret", clientSecret)
            append("grant_type", "authorization_code")
            append("redirect_uri", redirectUrl)
        }
        if (response.status.isSuccess()) {
            val token = response.body<MicrosoftAccessTokenResponse>()
            return Result.success(token.accessToken)
        }
        val error = response.body<MicrosoftAccessTokenErrorResponse>()
        return Result.failure(GetMicrosoftTokenFailure.CouldNotGetToken(error.error, error.description))
    }
}