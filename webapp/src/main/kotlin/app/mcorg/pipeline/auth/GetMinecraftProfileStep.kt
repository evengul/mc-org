package app.mcorg.pipeline.auth

import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.auth.minecraft.MinecraftProfileResponse
import app.mcorg.pipeline.failure.GetMinecraftProfileFailure
import app.mcorg.pipeline.failure.ApiFailure
import app.mcorg.config.MinecraftApiConfig
import io.ktor.client.request.header
import io.ktor.http.*

object GetMinecraftProfileStep : Step<String, GetMinecraftProfileFailure, MinecraftProfile> {
    override suspend fun process(input: String): Result<GetMinecraftProfileFailure, MinecraftProfile> {
        val step = MinecraftApiConfig.getProvider()
            .get<String, GetMinecraftProfileFailure, MinecraftProfileResponse>(
                url = MinecraftApiConfig.getProfileUrl(),
                headerBuilder = { requestBuilder, accessToken ->
                    requestBuilder.header(HttpHeaders.Authorization, "Bearer $accessToken")
                },
                errorMapper = { apiFailure ->
                    when (apiFailure) {
                        is ApiFailure.HttpError -> GetMinecraftProfileFailure.CouldNotGetProfile
                        else -> GetMinecraftProfileFailure.CouldNotGetProfile
                    }
                }
            )

        return step.process(input).map { response ->
            MinecraftProfile(
                username = response.name,
                uuid = response.id
            )
        }
    }
}