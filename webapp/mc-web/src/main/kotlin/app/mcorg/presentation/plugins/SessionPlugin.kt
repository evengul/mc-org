package app.mcorg.presentation.plugins

import app.mcorg.pipeline.idea.createsession.CreateIdeaWizardSession
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.directorySessionStorage
import io.ktor.server.sessions.serialization.KotlinxSessionSerializer
import kotlinx.serialization.json.Json
import java.io.File

const val WIZARD_SESSION_COOKIE_NAME = "CREATE_IDEA_WIZARD_DATA"

fun Application.configureSessions() {
    install(Sessions) {
        cookie<CreateIdeaWizardSession>(WIZARD_SESSION_COOKIE_NAME, directorySessionStorage(File(".sessions/idea-wizard"), cached = true)) {
            cookie.path = "/app/ideas/create"
            cookie.maxAgeInSeconds = 60 * 60 // 1 hour
            cookie.httpOnly = true
            cookie.secure = this@configureSessions.environment.config.propertyOrNull("ktor.deployment.sslPort") != null
            cookie.extensions["SameSite"] = "Strict"

            serializer = KotlinxSessionSerializer(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }
}