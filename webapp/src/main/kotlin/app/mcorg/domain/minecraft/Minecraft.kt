package app.mcorg.domain.minecraft

interface Minecraft {
    suspend fun getProfile(authorizationCode: String, clientId: String, clientSecret: String, host: String): MinecraftProfile
}