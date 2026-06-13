package app.mcorg.config

import app.mcorg.pipeline.Result
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
    override fun getUserAgent() = "evegul/seam/main (even.gultvedt@gmail.com)"

    override fun getContentType() = ContentType.Application.FormUrlEncoded

    fun getVersionsUrl() ="${baseUrl}/tag/game_version"
}

object MicrosoftLoginApiConfig : ApiConfig(AppConfig.microsoftLoginBaseUrl) {
    override fun getContentType() = ContentType.Application.Json

    fun getTokenUrl() = "${AppConfig.microsoftLoginBaseUrl}/consumers/oauth2/v2.0/token"
}

object XboxAuthApiConfig : ApiConfig(AppConfig.xboxAuthBaseUrl) {
    override fun getContentType() = ContentType.Application.Json

    fun getAuthenticateUrl() = "${AppConfig.xboxAuthBaseUrl}/user/authenticate"
}

object XstsAuthorizationApiConfig : ApiConfig(AppConfig.xstsAuthBaseUrl) {
    override fun getContentType() = ContentType.Application.Json

    fun getAuthorizeUrl() = "${AppConfig.xstsAuthBaseUrl}/xsts/authorize"
}

object MinecraftApiConfig : ApiConfig(AppConfig.minecraftBaseUrl) {
    override fun getContentType() = ContentType.Application.Json

    fun getAuthenticateUrl() = "${AppConfig.minecraftBaseUrl}/authentication/login_with_xbox"
    fun getProfileUrl() = "${AppConfig.minecraftBaseUrl}/minecraft/profile"
}

object MojangLauncherMetaApiConfig : ApiConfig(AppConfig.launcherMetaBaseUrl) {
    override fun getContentType() = ContentType.Application.Json

    fun getVersionManifestUrl() = "${baseUrl}/mc/game/version_manifest_v2.json"
}

class TestApiConfig : ApiConfig("https://api.example.com") {
    override fun getContentType(): ContentType = ContentType.Application.Json
    override fun acceptContentType(): ContentType = ContentType.Application.Json
    override fun getUserAgent(): String = "Seam-Test/1.0"
}
