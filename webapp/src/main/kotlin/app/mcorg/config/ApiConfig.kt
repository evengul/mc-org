package app.mcorg.config

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import io.ktor.http.*

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
    var fakeResponses : ((httpMethod: HttpMethod, url: String) -> Result<AppFailure.ApiError, String>)? = null

    fun useFakeProvider(responses: (httpMethod: HttpMethod, url: String) -> Result<AppFailure.ApiError, String>) {
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

object FabricMcApiConfig : ApiConfig(AppConfig.fabricMcBaseUrl) {
    override fun getContentType() = ContentType.Application.Json

    fun getVersionsUrl() = "${baseUrl}/versions/game"
}

object GithubGistsApiConfig : ApiConfig(AppConfig.githubGistsBaseUrl) {
    override fun getContentType() = ContentType.Application.Json

    fun getServerJarsUrl() = "${baseUrl}/cliffano/77a982a7503669c3e1acb0a0cf6127e9/raw/e91cfeacc56e461d5943e100a2bc7eb0919c0a83/minecraft-server-jar-downloads.md"
}

class TestApiConfig : ApiConfig("https://api.example.com") {
    override fun getContentType(): ContentType = ContentType.Application.Json
    override fun acceptContentType(): ContentType = ContentType.Application.Json
    override fun getUserAgent(): String = "MC-ORG-Test/1.0"
}