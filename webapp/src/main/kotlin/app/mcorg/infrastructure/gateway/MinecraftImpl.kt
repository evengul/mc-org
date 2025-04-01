package app.mcorg.infrastructure.gateway

import app.mcorg.domain.api.Minecraft
import app.mcorg.domain.model.minecraft.MinecraftProfile
import app.mcorg.model.Env
import app.mcorg.model.Local
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*

class MinecraftImpl : Minecraft, Gateway() {
    override suspend fun getProfile(authorizationCode: String, clientId: String, clientSecret: String, env: Env, host: String?): MinecraftProfile {
        try {
            val (error, microsoftAccessToken) = getTokenFromCode(authorizationCode, clientId, clientSecret, env, host)
            if (microsoftAccessToken != null) {
                val xboxProfile = getXboxProfile(microsoftAccessToken)
                val xboxToken = xboxProfile.token
                val userHash = xboxProfile.userHash()
                val xstsToken = getXstsToken(xboxToken).token
                val minecraftToken = getMinecraftToken(xstsToken, userHash).accessToken

                val minecraftProfile = getMinecraftProfile(minecraftToken)

                return MinecraftProfile(minecraftProfile.name, "unknown")
            }
            else if(error != null) {
                throw RuntimeException("Error [${error.error}] occurred while getting token: ${error.description}")
            }
            throw RuntimeException("Could not retrieve token")
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private suspend fun getMinecraftProfile(accessToken: String): MinecraftProfileResponse = getJsonClient().use {
        return it.get("https://api.minecraftservices.com/minecraft/profile") {
            bearerAuth(accessToken)
        }.body<MinecraftProfileResponse>()
    }

    private suspend fun getMinecraftToken(xstsToken: String, userHash: String): MinecraftTokenResponse = getJsonClient().use {
        return it.post("https://api.minecraftservices.com/authentication/login_with_xbox") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(MinecraftRequest(createMinecraftRequest(userHash = userHash, xstsToken = xstsToken)))
        }.body<MinecraftTokenResponse>()
    }

    private suspend fun getXstsToken(xboxToken: String): XboxTokenResponse = getJsonClient().use {
        return it.post("https://xsts.auth.xboxlive.com/xsts/authorize") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(XstsRequest(XstsProperties("RETAIL", listOf(xboxToken)), "rp://api.minecraftservices.com/", "JWT"))
        }.body()
    }

    private suspend fun getXboxProfile(microsoftAccessToken: String): XboxTokenResponse = getJsonClient().use {
        return it.post("https://user.auth.xboxlive.com/user/authenticate") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(XboxProfileRequest(XboxProperties("RPS", "user.auth.xboxlive.com", "d=$microsoftAccessToken"), "http://auth.xboxlive.com", "JWT"))
        }.body()
    }

    private suspend fun getTokenFromCode(authorizationCode: String, clientId: String, clientSecret: String, env: Env, host: String?): Pair<MicrosoftAccessTokenErrorResponse?, String?> = getJsonClient().use {
        val redirectUrl =
            if (env == Local) "http://localhost:8080/auth/oidc/microsoft-redirect"
            else if (host != null) "https://$host/auth/oidc/microsoft-redirect"
            else throw IllegalArgumentException("Host cannot be null for a non-local environment")

        val response = it.get(url = Url("https://login.microsoftonline.com/consumers/oauth2/v2.0/token")) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(Parameters.build {
                append("code", authorizationCode)
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("grant_type", "authorization_code")
                append("redirect_uri", redirectUrl)
            }))
        }
        if (response.status != HttpStatusCode.OK) {
            val error = response.body<MicrosoftAccessTokenErrorResponse>()
            return error to null
        }
        return null to response.body<MicrosoftAccessTokenResponse>().accessToken
    }
}