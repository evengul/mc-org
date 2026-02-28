package app.mcorg.pipeline.idea.createsession

import app.mcorg.domain.model.idea.Author
import app.mcorg.presentation.plugins.WIZARD_SESSION_COOKIE_NAME
import app.mcorg.presentation.utils.getUser
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.sessions

fun ApplicationCall.getWizardSession(): CreateIdeaWizardSession {
    val user = getUser()
    return sessions.get(WIZARD_SESSION_COOKIE_NAME) as CreateIdeaWizardSession? ?: CreateIdeaWizardSession(userId = user.id, author = Author.SingleAuthor(user.minecraftUsername)).also {
        sessions.set(WIZARD_SESSION_COOKIE_NAME, it)
    }
}

fun ApplicationCall.updateWizardSession(update: CreateIdeaWizardSession.() -> CreateIdeaWizardSession) {
    val updatedSession = getWizardSession().update().copy(
        lastModified = System.currentTimeMillis()
    )
    sessions.set(WIZARD_SESSION_COOKIE_NAME, updatedSession)
}

fun ApplicationCall.markFieldAsManuallyEdited(field: WizardField) {
    updateWizardSession {
        copy(
            dataSource = dataSource + (field to DataSource.MANUAL_ENTRY)
        )
    }
}

fun ApplicationCall.clearWizardSession() {
    sessions.clear(WIZARD_SESSION_COOKIE_NAME)
}