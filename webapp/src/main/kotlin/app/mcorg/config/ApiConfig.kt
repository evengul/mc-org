package app.mcorg.config

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ApiFailure
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod

sealed class ApiConfig {
    abstract fun getBaseUrl(): String
    abstract fun getContentType(): ContentType
    open fun acceptContentType(): ContentType = ContentType.Application.Json
    open fun getUserAgent(): String? = null

    enum class ProviderType {
        DEFAULT,
        FAKE
    }

    var provider: ProviderType = ProviderType.DEFAULT
    var fakeResponses : ((httpMethod: HttpMethod, url: String) -> Result<ApiFailure, String>)? = null

    fun useFakeProvider(responses: (httpMethod: HttpMethod, url: String) -> Result<ApiFailure, String>) {
        fakeResponses = responses
        provider = ProviderType.FAKE
    }

    fun resetProvider() {
        fakeResponses = null
        provider = ProviderType.DEFAULT
    }

    fun getProvider(): ApiProvider {
        return when(provider) {
            ProviderType.DEFAULT -> DefaultApiProvider(this)
            ProviderType.FAKE -> FakeApiProvider(this, fakeResponses ?: throw IllegalStateException("Fake responses must be provided for FAKE provider"))
        }
    }
}

object ModrinthApiConfig : ApiConfig() {
    override fun getBaseUrl() = "https://api.modrinth.com/v2"

    override fun getUserAgent() = "evegul/mcorg/main (even@mcorg.com)"

    override fun getContentType() = ContentType.Application.FormUrlEncoded

    fun getVersionsUrl() ="${getBaseUrl()}/tag/game_version"
}

object MicrosoftLoginApiConfig : ApiConfig() {
    override fun getBaseUrl()  ="https://login.microsoftonline.com"

    override fun getContentType() = ContentType.Application.Json

    fun getTokenUrl() = "${getBaseUrl()}/consumers/oauth2/v2.0/token"
}

object XboxAuthApiConfig : ApiConfig() {
    override fun getBaseUrl() = "https://user.auth.xboxlive.com"

    override fun getContentType() = ContentType.Application.Json

    fun getAuthenticateUrl() = "${getBaseUrl()}/user/authenticate"
}

object XstsAuthorizationApiConfig : ApiConfig() {
    override fun getBaseUrl() = "https://xsts.auth.xboxlive.com"

    override fun getContentType() = ContentType.Application.Json

    fun getAuthorizeUrl() = "${getBaseUrl()}/xsts/authorize"
}

object MinecraftApiConfig : ApiConfig() {
    override fun getBaseUrl() = "https://api.minecraftservices.com"

    override fun getContentType() = ContentType.Application.Json

    fun getAuthenticateUrl() = "${getBaseUrl()}/authentication/login_with_xbox"
    fun getProfileUrl() = "${getBaseUrl()}/minecraft/profile"
}

class TestApiConfig : ApiConfig() {
    override fun getBaseUrl(): String = "https://api.example.com"
    override fun getContentType(): ContentType = ContentType.Application.Json
    override fun acceptContentType(): ContentType = ContentType.Application.Json
    override fun getUserAgent(): String = "MC-ORG-Test/1.0"
}