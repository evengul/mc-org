package app.mcorg.pipeline.auth

import app.mcorg.config.*
import app.mcorg.domain.Env
import app.mcorg.domain.Local
import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.auth.commonsteps.AddCookieStep
import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.pipeline.auth.commonsteps.CreateUserIfNotExistsStep
import app.mcorg.pipeline.auth.commonsteps.UpdateLastSignInStep
import app.mcorg.pipeline.auth.domain.*
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.getHost
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLDecoder

suspend fun ApplicationCall.handleSignIn() {
    val redirectPath = parameters["state"]?.let { URLDecoder.decode(it, Charsets.UTF_8) } ?: "/"

    executePipeline(
        onSuccess = { respondRedirect(redirectPath) },
        onFailure = { when(it) {
            is AppFailure.Redirect -> respondRedirect(it.toUrl())
            is AppFailure.ApiError -> respondRedirect("/auth/sign-out?error=external_api_error")
            else -> respondRedirect("/auth/sign-out?error=internal_error")
        } }
    ) {
        value(parameters)
            .step(GetMicrosoftCodeStep)
            .map {
                GetMicrosoftTokenInput(
                    code = it,
                    clientId = AppConfig.microsoftClientId,
                    clientSecret = AppConfig.microsoftClientSecret,
                    env = AppConfig.env,
                    host = getHost()
                )
            }
            .step(GetMicrosoftTokenStep)
            .step(GetXboxProfileStep)
            .step(GetXstsToken)
            .step(GetMinecraftToken)
            .step(GetMinecraftProfileStep)
            .step(CreateUserIfNotExistsStep)
            .step(object : Step<TokenProfile, AppFailure, String> {
                override suspend fun process(input: TokenProfile): Result<AppFailure, String> {
                    return when (val tokenResult = CreateTokenStep.process(input)) {
                        is Result.Success -> AddCookieStep(response.cookies, getHost() ?: "false").process(tokenResult.value).map { input.minecraftUsername }
                        is Result.Failure -> tokenResult
                    }
                }
            })
            .step(UpdateLastSignInStep)
    }
}

object GetMicrosoftCodeStep : Step<Parameters, AppFailure, String> {
    override suspend fun process(input: Parameters): Result<AppFailure, String> {
        val code = input["code"]
        val error = input["error"]
        val description = input["description"]

        if (error != null) {
            return Result.failure(AppFailure.Redirect(
                path = "/auth/sign-out",
                queryParameters = buildMap {
                    put("microsoft_error", error)
                    if (description != null) {
                        put("microsoft_description", description)
                    }
                }
            ))
        }

        if (code == null) {
            return Result.failure(AppFailure.Redirect(
                path = "/auth/sign-out",
                queryParameters = mapOf("error" to "missing_code")
            ))
        }

        return Result.success(code)
    }
}

data class GetMicrosoftTokenInput(
    val code: String,
    val clientId: String,
    val clientSecret: String,
    val env: Env,
    val host: String?
)

object GetMicrosoftTokenStep : Step<GetMicrosoftTokenInput, AppFailure, String> {
    override suspend fun process(input: GetMicrosoftTokenInput): Result<AppFailure, String> {
        val (code, clientId, clientSecret, env, host) = input
        val redirectUrl =
            if (env == Local) "http://localhost:8080/auth/oidc/microsoft-redirect"
            else if (host != null) "https://$host/auth/oidc/microsoft-redirect"
            else return Result.failure(AppFailure.IllegalConfigurationError("No host provided for non-local environment."))

        val step = MicrosoftLoginApiConfig.getProvider()
            .post<GetMicrosoftTokenInput, MicrosoftAccessTokenResponse>(
                url = MicrosoftLoginApiConfig.getTokenUrl(),
                bodyBuilder = { requestBuilder, _ ->
                    requestBuilder.setBody(FormDataContent(Parameters.build {
                        append("code", code)
                        append("client_id", clientId)
                        append("client_secret", clientSecret)
                        append("grant_type", "authorization_code")
                        append("redirect_uri", redirectUrl)
                    }))
                    requestBuilder.contentType(ContentType.Application.FormUrlEncoded)
                }
            )

        return step.process(input).map { response -> response.accessToken }
    }
}

object GetXboxProfileStep : Step<String, AppFailure.ApiError, TokenData> {

    override suspend fun process(input: String): Result<AppFailure.ApiError, TokenData> {
        val step = XboxAuthApiConfig.getProvider()
            .post<String, XboxTokenResponse>(
                url = XboxAuthApiConfig.getAuthenticateUrl(),
                bodyBuilder = { requestBuilder, accessToken ->
                    val body = XboxProfileRequest(
                        XboxProperties("RPS", "user.auth.xboxlive.com", "d=$accessToken"),
                        "http://auth.xboxlive.com",
                        "JWT"
                    )
                    requestBuilder.setBody(body)
                }
            )

        return step.process(input).map { response ->
            TokenData(response.token, response.userHash())
        }
    }
}

object GetXstsToken : Step<TokenData, AppFailure.ApiError, TokenData> {
    override suspend fun process(input: TokenData): Result<AppFailure.ApiError, TokenData> {
        val step = XstsAuthorizationApiConfig.getProvider()
            .post<TokenData, XboxTokenResponse>(
                url = XstsAuthorizationApiConfig.getAuthorizeUrl(),
                bodyBuilder = { requestBuilder, tokenData ->
                    val body = XstsRequest(
                        XstsProperties("RETAIL", listOf(tokenData.token)),
                        "rp://api.minecraftservices.com/",
                        "JWT"
                    )
                    requestBuilder.setBody(body)
                }
            )

        return step.process(input).map { response ->
            TokenData(response.token, input.hash)
        }
    }
}

object GetMinecraftToken : Step<TokenData, AppFailure.ApiError, String> {
    override suspend fun process(input: TokenData): Result<AppFailure.ApiError, String> {
        val step = MinecraftApiConfig.getProvider()
            .post<TokenData, MinecraftTokenResponse>(
                url = MinecraftApiConfig.getAuthenticateUrl(),
                bodyBuilder = { requestBuilder, tokenData ->
                    val body = MinecraftRequest(createMinecraftRequest(userHash = tokenData.hash, xstsToken = tokenData.token))
                    requestBuilder.setBody(body)
                }
            )

        return step.process(input).map { response -> response.accessToken }
    }
}

object GetMinecraftProfileStep : Step<String, AppFailure.ApiError, MinecraftProfile> {
    val logger: Logger = LoggerFactory.getLogger(GetMinecraftProfileStep::class.java)
    override suspend fun process(input: String): Result<AppFailure.ApiError, MinecraftProfile> {
        val step = MinecraftApiConfig.getProvider()
            .get<String, MinecraftProfileResponse>(
                url = MinecraftApiConfig.getProfileUrl(),
                headerBuilder = { requestBuilder, accessToken ->
                    requestBuilder.header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            )

        return step.process(input).map { response ->
            MinecraftProfile(
                username = response.name,
                uuid = response.id,
                isDemoUser = false
            )
        }
    }
}