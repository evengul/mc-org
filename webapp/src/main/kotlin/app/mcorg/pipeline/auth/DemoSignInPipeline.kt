package app.mcorg.pipeline.auth

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.pipeline.auth.commonsteps.AddCookieStep
import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.pipeline.auth.commonsteps.CreateUserIfNotExistsStep
import app.mcorg.pipeline.auth.commonsteps.UpdateLastSignInStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.getHost
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlin.random.Random

suspend fun ApplicationCall.handleDemoSignIn() {
    val demoUsername = if (AppConfig.env == Production) {
        AppConfig.demoUser
    } else when(val requestedUsername = request.queryParameters["username"]?.trim()) {
        null, "" -> AppConfig.demoUser
        "random" -> "DemoUser_${Random.nextInt(1000, 9999)}"
        else -> requestedUsername
    }
    val demoUuid = "${demoUsername}-uuid"
    val redirectPath = parameters["redirect_to"] ?: "/"

    executePipeline(
        onSuccess = { respondRedirect(redirectPath) },
        onFailure = { respond(HttpStatusCode.Forbidden) }
    ) {
        value(MinecraftProfile(uuid = demoUuid, username = demoUsername, isDemoUser = true))
            .step(CreateUserIfNotExistsStep)
            .step(CreateTokenStep)
            .step(AddCookieStep(response.cookies, getHost() ?: "false"))
            .value(demoUsername)
            .step(UpdateLastSignInStep)
    }
}