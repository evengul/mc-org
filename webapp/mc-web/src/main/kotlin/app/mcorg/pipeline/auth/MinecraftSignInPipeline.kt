package app.mcorg.pipeline.auth

import app.mcorg.config.*
import app.mcorg.domain.Env
import app.mcorg.domain.Local
import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.pipeline
import app.mcorg.pipeline.auth.commonsteps.AddCookieStep
import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.pipeline.auth.commonsteps.CreateUserIfNotExistsStep
import app.mcorg.pipeline.auth.commonsteps.UpdateLastSignInStep
import app.mcorg.pipeline.auth.domain.*
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.security.OAUTH_STATE_COOKIE
import app.mcorg.presentation.security.clearOAuthNonce
import app.mcorg.presentation.security.decodeOAuthState
import app.mcorg.presentation.security.nonceMatches
import app.mcorg.presentation.security.safeRedirectPath
import app.mcorg.presentation.utils.getHost
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val signInLogger: Logger = LoggerFactory.getLogger("app.mcorg.pipeline.auth.MinecraftSignIn")

suspend fun ApplicationCall.handleSignIn() {
    // `parameters` is already percent-decoded by Ktor. A second java.net.URLDecoder.decode() pass
    // used to sit here, and it throws IllegalArgumentException on anything that decodes to a stray
    // `%` — so `?state=%25zz` reached this line, threw before the nonce check and before any
    // try/catch, and produced a 500 plus a stack trace from an unauthenticated, scriptable
    // request. This handler is deliberately reachable without a session (AuthPlugin exempts
    // `/oidc`), which is exactly why it has to fail closed on garbage rather than throw.
    //
    // Dropping the second decode does not weaken the redirect guard: safeRedirectPath below still
    // runs on the decoded value, so `%2F%2Fevil.example` arrives as `//evil.example` and is caught.
    val state = decodeOAuthState(parameters["state"])

    // Nonce check first, before anything spends the authorization code (MCO-355). An attacker can
    // choose the `state` on a URL they send a victim, but cannot set a cookie for this origin, so
    // they cannot make the two halves agree. A missing, stale or foreign nonce means this callback
    // is not the continuation of a sign-in *this browser* started.
    val nonceOk = state != null && nonceMatches(state.first, request.cookies[OAUTH_STATE_COOKIE])
    response.cookies.clearOAuthNonce()
    if (!nonceOk) {
        signInLogger.warn("Rejected an OAuth callback with a missing or mismatched state nonce")
        respondRedirect("/auth/sign-in?error=invalid_state")
        return
    }

    // Validated *after* decoding, which is the point: it is the decode that turns
    // `%2F%2Fevil.example` back into the scheme-relative `//evil.example` a browser would follow
    // off-origin. `state` round-trips through a genuine login.microsoftonline.com authorize URL,
    // so an attacker who crafts that URL chooses this value and the victim arrives here from a
    // real sign-in — the moment they are least likely to look at the address bar (MCO-352).
    val redirectPath = safeRedirectPath(state.second, fallback = "/")

    pipeline(
        onSuccess = { respondRedirect(redirectPath) },
        onFailure = { error: AppFailure ->
            when(error) {
                is AppFailure.Redirect -> respondRedirect(error.toUrl())
                is AppFailure.ApiError -> respondRedirect("/auth/sign-out?error=external_api_error")
                else -> respondRedirect("/auth/sign-out?error=internal_error")
            }
        }
    ) {
        val code = GetMicrosoftCodeStep.run(parameters)
        val tokenInput = GetMicrosoftTokenInput(
            code = code,
            clientId = AppConfig.microsoftClientId,
            clientSecret = AppConfig.microsoftClientSecret,
            env = AppConfig.env,
            host = getHost()
        )
        val msToken = GetMicrosoftTokenStep.run(tokenInput)
        val xboxProfile = GetXboxProfileStep.run(msToken)
        val xstsToken = GetXstsToken.run(xboxProfile)
        val mcToken = GetMinecraftToken.run(xstsToken)
        val mcProfile = GetMinecraftProfileStep.run(mcToken)
        val user = CreateUserIfNotExistsStep.run(mcProfile)
        val token = CreateTokenStep.run(user)
        AddCookieStep(response.cookies, getHost() ?: "false").run(token)
        UpdateLastSignInStep.run(user.minecraftUsername)
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
    val host: String?,
    // Defaulted rather than threaded through every construction site: only LOCAL uses it, and the
    // step's other config already arrives as input so tests can vary it. See [microsoftRedirectUri].
    val port: Int = AppConfig.port,
)

object GetMicrosoftTokenStep : Step<GetMicrosoftTokenInput, AppFailure, String> {
    override suspend fun process(input: GetMicrosoftTokenInput): Result<AppFailure, String> {
        val (code, clientId, clientSecret, env, host, port) = input
        if (env != Local && host == null) {
            return Result.failure(AppFailure.IllegalConfigurationError("No host provided for non-local environment."))
        }
        val redirectUrl = microsoftRedirectUri(env, host, port)

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
                // No retrySafe here, deliberately (MCO-354). `code` is single-use: Microsoft has
                // already consumed it by the time a 5xx comes back, so replaying it converts a
                // recoverable upstream blip into a guaranteed failed sign-in. This is the one
                // call in the chain that must not be retried, which is why the default is off
                // and the other three opt in explicitly.
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
                },
                // Safe to replay: this re-presents an access token we still hold rather than
                // spending anything. Xbox Live is the flakiest hop in the chain (MCO-354).
                retrySafe = true,
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
                },
                // Re-presents the Xbox token; consumes nothing (MCO-354).
                retrySafe = true,
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
                },
                // Re-presents the XSTS token; consumes nothing. api.minecraftservices.com is the
                // other historically flaky hop (MCO-354).
                retrySafe = true,
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
