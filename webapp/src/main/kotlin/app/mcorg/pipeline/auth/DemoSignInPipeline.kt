package app.mcorg.pipeline.auth

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.pipeline.auth.commonsteps.AddCookieStep
import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.pipeline.auth.commonsteps.CreateUserIfNotExistsStep
import app.mcorg.pipeline.auth.commonsteps.UpdateLastSignInStep
import app.mcorg.presentation.utils.getHost
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import kotlin.random.Random

sealed interface DemoSignInFailure {
    object DatabaseError : DemoSignInFailure
    object TokenError : DemoSignInFailure
}

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
    Pipeline.create<DemoSignInFailure, Unit>()
        .map { AppConfig.env }
        .map { MinecraftProfile(uuid = demoUuid, username = demoUsername, isDemoUser = true) }
        .wrapPipe(CreateUserIfNotExistsStep) {
            it.mapError { DemoSignInFailure.DatabaseError }
        }
        .wrapPipe(CreateTokenStep) {
            it.mapError {
                DemoSignInFailure.TokenError
            }
        }
        .wrapPipe(AddCookieStep(response.cookies, getHost() ?: "false")) {
            it.mapError {
                DemoSignInFailure.TokenError
            }
        }
        .map { demoUsername }
        .wrapPipe(UpdateLastSignInStep) {
            it.mapError { DemoSignInFailure.DatabaseError }
        }
        .fold(
            input = Unit,
            onFailure = { respond(HttpStatusCode.Forbidden) },
            onSuccess = {
                respondRedirect(redirectPath)
            }
        )
}