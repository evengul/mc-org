package app.mcorg.domain.api

import app.mcorg.domain.model.minecraft.MinecraftProfile

interface Minecraft {
    suspend fun getProfile(authorizationCode: String, clientId: String, clientSecret: String, env: String, host: String?): MinecraftProfile
}