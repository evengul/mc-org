package app.mcorg.config

import io.ktor.http.ContentType

interface ApiProvider {
    fun getBaseUrl(): String
    fun getContentType(): ContentType
    fun acceptContentType(): ContentType = ContentType.Application.Json
    fun getUserAgent(): String? = null
}

object ModrinthApiProvider : ApiProvider {
    override fun getBaseUrl() = "https://api.modrinth.com/v2"

    override fun getUserAgent() = "evegul/mcorg/main (even@mcorg.com)"

    override fun getContentType() = ContentType.Application.FormUrlEncoded

    fun getVersionsUrl() ="${getBaseUrl()}/tag/game_version"
}

object MicrosoftLoginApiProvider : ApiProvider {
    override fun getBaseUrl()  ="https://login.microsoftonline.com"

    override fun getContentType() = ContentType.Application.Json

    fun getTokenUrl() = "${getBaseUrl()}/consumers/oauth2/v2.0/token"
}

object XboxAuthApiProvider : ApiProvider {
    override fun getBaseUrl() = "https://user.auth.xboxlive.com"

    override fun getContentType() = ContentType.Application.Json

    fun getAuthenticateUrl() = "${getBaseUrl()}/user/authenticate"
}

object XstsAuthorizationApiProvider : ApiProvider {
    override fun getBaseUrl() = "https://xsts.auth.xboxlive.com"

    override fun getContentType() = ContentType.Application.Json

    fun getAuthorizeUrl() = "${getBaseUrl()}/xsts/authorize"
}

object MinecraftApiProvider : ApiProvider {
    override fun getBaseUrl() = "https://api.minecraftservices.com"

    override fun getContentType() = ContentType.Application.Json

    fun getAuthenticateUrl() = "${getBaseUrl()}/authentication/login_with_xbox"
    fun getProfileUrl() = "${getBaseUrl()}/minecraft/profile"
}