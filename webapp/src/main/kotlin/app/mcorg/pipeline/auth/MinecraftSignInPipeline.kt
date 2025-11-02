package app.mcorg.pipeline.auth

import app.mcorg.config.AppConfig
import app.mcorg.config.MicrosoftLoginApiConfig
import app.mcorg.config.MinecraftApiConfig
import app.mcorg.config.XboxAuthApiConfig
import app.mcorg.config.XstsAuthorizationApiConfig
import app.mcorg.domain.Env
import app.mcorg.domain.Local
import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.auth.commonsteps.AddCookieStep
import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.pipeline.auth.commonsteps.CreateUserIfNotExistsStep
import app.mcorg.pipeline.auth.commonsteps.UpdateLastSignInStep
import app.mcorg.pipeline.auth.domain.MicrosoftAccessTokenResponse
import app.mcorg.pipeline.auth.domain.TokenData
import app.mcorg.pipeline.auth.domain.MinecraftProfileResponse
import app.mcorg.pipeline.auth.domain.MinecraftRequest
import app.mcorg.pipeline.auth.domain.MinecraftTokenResponse
import app.mcorg.pipeline.auth.domain.XboxProfileRequest
import app.mcorg.pipeline.auth.domain.XboxProperties
import app.mcorg.pipeline.auth.domain.XboxTokenResponse
import app.mcorg.pipeline.auth.domain.XstsProperties
import app.mcorg.pipeline.auth.domain.XstsRequest
import app.mcorg.pipeline.auth.domain.createMinecraftRequest
import app.mcorg.pipeline.failure.ApiFailure
import app.mcorg.presentation.utils.getHost
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondRedirect
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLDecoder

sealed interface MicrosoftSignInFailure {
    object MissingCode : MicrosoftSignInFailure
    data class MicrosoftError(val error: String, val description: String) : MicrosoftSignInFailure
    object NoHostForNonLocalEnv : MicrosoftSignInFailure
    object DatabaseError : MicrosoftSignInFailure
    object TokenError : MicrosoftSignInFailure
}

suspend fun ApplicationCall.handleSignIn() {
    val redirectPath = parameters["state"]?.let { URLDecoder.decode(it, Charsets.UTF_8) } ?: "/"
    var username = "system" // Default in case of failure
    Pipeline.create<MicrosoftSignInFailure, Unit>()
        .map { parameters }
        .pipe(GetMicrosoftCodeStep)
        .map {
            GetMicrosoftTokenInput(
                code = it,
                clientId = AppConfig.microsoftClientId,
                clientSecret = AppConfig.microsoftClientSecret,
                env = AppConfig.env,
                host = getHost()
            )
        }
        .pipe(GetMicrosoftTokenStep)
        .pipe(GetXboxProfileStep)
        .pipe(GetXstsToken)
        .pipe(GetMinecraftToken)
        .pipe(GetMinecraftProfileStep)
        .wrapPipe(CreateUserIfNotExistsStep) {
            it.mapError { MicrosoftSignInFailure.DatabaseError }
        }
        .peek { username = it.minecraftUsername }
        .wrapPipe(CreateTokenStep) {
            it.mapError { MicrosoftSignInFailure.TokenError}
        }
        .wrapPipe(AddCookieStep(response.cookies, getHost() ?: "false")) {
            it.mapError { MicrosoftSignInFailure.TokenError }
        }
        .map { username }
        .wrapPipe(UpdateLastSignInStep) {
            it.mapError { MicrosoftSignInFailure.DatabaseError }
        }
        .fold(
            input = Unit,
            onFailure = {
                val queryParams = when(it) {
                    MicrosoftSignInFailure.DatabaseError -> "error=unknown"
                    is MicrosoftSignInFailure.MicrosoftError -> "error=${it.error}&message=${URLDecoder.decode(it.description, Charsets.UTF_8)}"
                    MicrosoftSignInFailure.MissingCode -> "error=missing_code"
                    MicrosoftSignInFailure.NoHostForNonLocalEnv -> "error=misconfigured&message=${URLDecoder.decode("No host provided for non-local environment.", Charsets.UTF_8)}"
                    MicrosoftSignInFailure.TokenError -> "error=token_error"
                }
                respondRedirect("/auth/sign-out?$queryParams")
            },
            onSuccess = { respondRedirect(redirectPath) }
        )
}

object GetMicrosoftCodeStep : Step<Parameters, MicrosoftSignInFailure, String> {
    override suspend fun process(input: Parameters): Result<MicrosoftSignInFailure, String> {
        val code = input["code"]
        if (code != null) {
            return Result.success(code)
        }

        val error = input["error"]
        val description = input["description"]

        return if (error != null) {
            Result.failure(MicrosoftSignInFailure.MicrosoftError(error, description ?: "Some error occurred"))
        } else {
            Result.failure(MicrosoftSignInFailure.MissingCode)
        }
    }
}

data class GetMicrosoftTokenInput(
    val code: String,
    val clientId: String,
    val clientSecret: String,
    val env: Env,
    val host: String?
)

object GetMicrosoftTokenStep : Step<GetMicrosoftTokenInput, MicrosoftSignInFailure, String> {
    override suspend fun process(input: GetMicrosoftTokenInput): Result<MicrosoftSignInFailure, String> {
        val (code, clientId, clientSecret, env, host) = input
        val redirectUrl =
            if (env == Local) "http://localhost:8080/auth/oidc/microsoft-redirect"
            else if (host != null) "https://$host/auth/oidc/microsoft-redirect"
            else return Result.failure(MicrosoftSignInFailure.NoHostForNonLocalEnv)

        val step = MicrosoftLoginApiConfig.getProvider()
            .post<GetMicrosoftTokenInput, MicrosoftSignInFailure, MicrosoftAccessTokenResponse>(
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
                },
                errorMapper = { apiFailure ->
                    when (apiFailure) {
                        is ApiFailure.HttpError -> MicrosoftSignInFailure.MicrosoftError("http_error", "HTTP ${apiFailure.statusCode}")
                        else -> MicrosoftSignInFailure.MicrosoftError("api_error", apiFailure.toString())
                    }
                }
            )

        return step.process(input).map { response -> response.accessToken }
    }
}

object GetXboxProfileStep : Step<String, MicrosoftSignInFailure, TokenData> {

    private val logger = LoggerFactory.getLogger(GetXboxProfileStep::class.java)

    override suspend fun process(input: String): Result<MicrosoftSignInFailure, TokenData> {
        val step = XboxAuthApiConfig.getProvider()
            .post<String, MicrosoftSignInFailure, XboxTokenResponse>(
                url = XboxAuthApiConfig.getAuthenticateUrl(),
                bodyBuilder = { requestBuilder, accessToken ->
                    val body = XboxProfileRequest(
                        XboxProperties("RPS", "user.auth.xboxlive.com", "d=$accessToken"),
                        "http://auth.xboxlive.com",
                        "JWT"
                    )
                    requestBuilder.setBody(body)
                },
                errorMapper = { apiFailure ->
                    logger.error("Could not get Xbox profile: ${if(apiFailure is ApiFailure.HttpError) "HTTP ${apiFailure.statusCode}" else "$apiFailure"}")
                    MicrosoftSignInFailure.MicrosoftError("xbox_profile_error", "Failed to get Xbox profile.")
                }
            )

        return step.process(input).map { response ->
            TokenData(response.token, response.userHash())
        }
    }
}

object GetXstsToken : Step<TokenData, MicrosoftSignInFailure, TokenData> {
    private val logger = LoggerFactory.getLogger(GetXstsToken::class.java)
    override suspend fun process(input: TokenData): Result<MicrosoftSignInFailure, TokenData> {
        val step = XstsAuthorizationApiConfig.getProvider()
            .post<TokenData, MicrosoftSignInFailure, XboxTokenResponse>(
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
                    logger.error("Could not get XSTS token: ${if(apiFailure is ApiFailure.HttpError) "HTTP ${apiFailure.statusCode}" else "$apiFailure"}")
                    MicrosoftSignInFailure.MicrosoftError("xsts_token_error", "Failed to get XSTS token.")
                }
            )

        return step.process(input).map { response ->
            TokenData(response.token, input.hash)
        }
    }
}

object GetMinecraftToken : Step<TokenData, MicrosoftSignInFailure, String> {
    val logger: Logger = LoggerFactory.getLogger(GetMinecraftToken::class.java)
    override suspend fun process(input: TokenData): Result<MicrosoftSignInFailure, String> {
        val step = MinecraftApiConfig.getProvider()
            .post<TokenData, MicrosoftSignInFailure, MinecraftTokenResponse>(
                url = MinecraftApiConfig.getAuthenticateUrl(),
                bodyBuilder = { requestBuilder, tokenData ->
                    val body = MinecraftRequest(createMinecraftRequest(userHash = tokenData.hash, xstsToken = tokenData.token))
                    requestBuilder.setBody(body)
                },
                errorMapper = { apiFailure ->
                    logger.error("Could not get Minecraft token: ${if(apiFailure is ApiFailure.HttpError) "HTTP ${apiFailure.statusCode}" else "$apiFailure"}")
                    MicrosoftSignInFailure.MicrosoftError("minecraft_token_error", "Failed to get Minecraft token.")
                }
            )

        return step.process(input).map { response -> response.accessToken }
    }
}

object GetMinecraftProfileStep : Step<String, MicrosoftSignInFailure, MinecraftProfile> {
    val logger: Logger = LoggerFactory.getLogger(GetMinecraftProfileStep::class.java)
    override suspend fun process(input: String): Result<MicrosoftSignInFailure, MinecraftProfile> {
        val step = MinecraftApiConfig.getProvider()
            .get<String, MicrosoftSignInFailure, MinecraftProfileResponse>(
                url = MinecraftApiConfig.getProfileUrl(),
                headerBuilder = { requestBuilder, accessToken ->
                    requestBuilder.header(HttpHeaders.Authorization, "Bearer $accessToken")
                },
                errorMapper = { apiFailure ->
                    logger.error("Could not get Minecraft profile: ${if(apiFailure is ApiFailure.HttpError) "HTTP ${apiFailure.statusCode}" else "$apiFailure"}")
                    MicrosoftSignInFailure.MicrosoftError("minecraft_profile_error", "Failed to get Minecraft profile.")
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