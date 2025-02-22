package app.mcorg.domain.minecraft

interface Minecraft {
    suspend fun getProfile(authorizationCode: String, clientId: String, clientSecret: String, env: String, host: String?): MinecraftProfile
}