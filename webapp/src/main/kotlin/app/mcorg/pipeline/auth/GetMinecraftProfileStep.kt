package app.mcorg.pipeline.auth

import app.mcorg.domain.model.minecraft.MinecraftProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.auth.minecraft.MinecraftProfileResponse
import app.mcorg.pipeline.apiGetJson
import io.ktor.client.call.body
import io.ktor.http.isSuccess

sealed interface GetMinecraftProfileFailure : SignInWithMinecraftFailure {
    data object CouldNotGetProfile : GetMinecraftProfileFailure
}

object GetMinecraftProfileStep : Step<String, GetMinecraftProfileFailure, MinecraftProfile> {
    override suspend fun process(input: String): Result<GetMinecraftProfileFailure, MinecraftProfile> {
        val url = "https://api.minecraftservices.com/minecraft/profile"
        val response = apiGetJson(url, accessToken = input)
        if (response.status.isSuccess()) {
            val data = response.body<MinecraftProfileResponse>()
            return Result.success(
                MinecraftProfile(
                    username = data.name,
                    email = "unknown",
                    uuid = data.id
                )
            )
        }
        return Result.failure(GetMinecraftProfileFailure.CouldNotGetProfile)
    }
}