package app.mcorg.presentation.templated.layout.topbar

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.notification.GetUnreadNotificationCountStep
import kotlinx.html.*

/**
 * Creates a notification badge with unread count
 * Returns the count for display, or 0 if there's an error
 */
suspend fun getUnreadNotificationCount(user: TokenProfile): Int {
    return when (val result = GetUnreadNotificationCountStep.process(user.id)) {
        is Result.Success -> result.value
        is Result.Failure -> 0 // Gracefully handle errors by showing no badge
    }
}
