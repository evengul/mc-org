package app.mcorg.pipeline.auth

import app.mcorg.domain.model.v2.user.MinecraftProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.auth.minecraft.MinecraftProfileResponse
import app.mcorg.pipeline.failure.GetMinecraftProfileFailure
import app.mcorg.pipeline.failure.ApiFailure
import app.mcorg.pipeline.v2.ApiSteps
import app.mcorg.config.MinecraftApiProvider
import io.ktor.client.request.header
import io.ktor.http.*

object GetMinecraftProfileStep : Step<String, GetMinecraftProfileFailure, MinecraftProfile> {
    override suspend fun process(input: String): Result<GetMinecraftProfileFailure, MinecraftProfile> {
        val step = ApiSteps.getJson<String, GetMinecraftProfileFailure, MinecraftProfileResponse>(
            apiProvider = MinecraftApiProvider,
            url = MinecraftApiProvider.getProfileUrl(),
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