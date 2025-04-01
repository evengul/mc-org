package app.mcorg.domain.api

import app.mcorg.domain.model.minecraft.MinecraftProfile
import app.mcorg.model.Env

interface Minecraft {
    suspend fun getProfile(authorizationCode: String, clientId: String, clientSecret: String, env: Env, host: String?): MinecraftProfile
}