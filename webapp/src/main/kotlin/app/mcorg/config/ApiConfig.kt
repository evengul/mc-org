package app.mcorg.config

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ApiFailure
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod

sealed class ApiConfig(
    internal val baseUrl: String
) {
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

object ModrinthApiConfig : ApiConfig(AppConfig.modrinthBaseUrl) {
    override fun getUserAgent() = "evegul/mcorg/main (even@mcorg.com)"

    override fun getContentType() = ContentType.Application.FormUrlEncoded

    fun getVersionsUrl() ="${baseUrl}/tag/game_version"
}

object MicrosoftLoginApiConfig : ApiConfig(AppConfig.microsoftLoginBaseUrl) {
    override fun getContentType() = ContentType.Application.Json

    fun getTokenUrl() = "${baseUrl}/consumers/oauth2/v2.0/token"
}

object XboxAuthApiConfig : ApiConfig(AppConfig.xboxAuthBaseUrl) {
    override fun getContentType() = ContentType.Application.Json

    fun getAuthenticateUrl() = "${baseUrl}/user/authenticate"
}

object XstsAuthorizationApiConfig : ApiConfig(AppConfig.xstsAuthBaseUrl) {
    override fun getContentType() = ContentType.Application.Json

    fun getAuthorizeUrl() = "${baseUrl}/xsts/authorize"
}

object MinecraftApiConfig : ApiConfig(AppConfig.minecraftBaseUrl) {
    override fun getContentType() = ContentType.Application.Json

    fun getAuthenticateUrl() = "${baseUrl}/authentication/login_with_xbox"
    fun getProfileUrl() = "${baseUrl}/minecraft/profile"
}

class TestApiConfig : ApiConfig("https://api.example.com") {
    override fun getContentType(): ContentType = ContentType.Application.Json
    override fun acceptContentType(): ContentType = ContentType.Application.Json
    override fun getUserAgent(): String = "MC-ORG-Test/1.0"
}