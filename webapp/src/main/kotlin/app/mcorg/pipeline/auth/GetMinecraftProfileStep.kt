package app.mcorg.pipeline.auth

import app.mcorg.domain.model.minecraft.MinecraftProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.infrastructure.gateway.MinecraftProfileResponse
import app.mcorg.pipeline.apiGetJson
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking

sealed interface GetMinecraftProfileFailure : SignInWithMinecraftFailure {
    data object CouldNotGetProfile : GetMinecraftProfileFailure
}

object GetMinecraftProfileStep : Step<String, GetMinecraftProfileFailure, MinecraftProfile> {
    override fun process(input: String): Result<GetMinecraftProfileFailure, MinecraftProfile> {
        return runBlocking {
            val url = "https://api.minecraftservices.com/minecraft/profile"
            val response = apiGetJson(url, accessToken = input)
            if (response.status.isSuccess()) {
                val data = response.body<MinecraftProfileResponse>()
                return@runBlocking Result.success(
                    MinecraftProfile(
                        username = data.name,
                        email = "unknown"
                    )
                )
            }
            return@runBlocking Result.failure(GetMinecraftProfileFailure.CouldNotGetProfile)
        }
    }
}