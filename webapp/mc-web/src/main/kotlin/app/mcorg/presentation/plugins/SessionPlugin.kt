package app.mcorg.presentation.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.sessions.Sessions

const val WIZARD_SESSION_COOKIE_NAME = "CREATE_IDEA_WIZARD_DATA"

fun Application.configureSessions() {
    install(Sessions) {
        // Session-based idea wizard has been replaced by database-backed drafts (MCO-137b).
        // Keeping the Sessions plugin installed in case other features add sessions in future.
    }
}
