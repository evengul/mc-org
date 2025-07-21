package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.auth.minecraft.MicrosoftAccessTokenResponse
import app.mcorg.domain.Env
import app.mcorg.domain.Local
import app.mcorg.pipeline.failure.GetMicrosoftTokenFailure
import app.mcorg.pipeline.failure.ApiFailure
import app.mcorg.pipeline.v2.ApiSteps
import app.mcorg.config.MicrosoftLoginApiProvider
import io.ktor.client.request.forms.*
import io.ktor.client.request.setBody
import io.ktor.http.*

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

        val step = ApiSteps.postJson<GetMicrosoftTokenInput, GetMicrosoftTokenFailure, MicrosoftAccessTokenResponse>(
            apiProvider = MicrosoftLoginApiProvider,
            url = MicrosoftLoginApiProvider.getTokenUrl(),
            bodyBuilder = { requestBuilder, _ ->
                requestBuilder.setBody(FormDataContent(Parameters.build {
                    append("code", code)
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("grant_type", "authorization_code")
                    append("redirect_uri", redirectUrl)
                }))
                requestBuilder.contentType(ContentType.Application.FormUrlEncoded)
            },
            errorMapper = { apiFailure ->
                when (apiFailure) {
                    is ApiFailure.HttpError -> GetMicrosoftTokenFailure.CouldNotGetToken("http_error", "HTTP ${apiFailure.statusCode}")
                    else -> GetMicrosoftTokenFailure.CouldNotGetToken("api_error", apiFailure.toString())
                }
            }
        )

        return step.process(input).map { response -> response.accessToken }
    }
}