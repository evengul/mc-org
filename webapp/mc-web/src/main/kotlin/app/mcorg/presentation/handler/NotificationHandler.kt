package app.mcorg.presentation.handler

import app.mcorg.pipeline.notification.handleGetNotifications
import app.mcorg.pipeline.notification.handleMarkAllNotificationsRead
import app.mcorg.pipeline.notification.handleMarkNotificationRead
import app.mcorg.presentation.plugins.NotificationParamPlugin
import io.ktor.server.routing.*

class NotificationHandler {
    fun Route.notificationRoutes() {
        route("/notifications") {
            get {
                call.handleGetNotifications()
            }
            patch("/read") {
                call.handleMarkAllNotificationsRead()
            }
            route("/{notificationId}") {
                install(NotificationParamPlugin)
                patch("/read") {
                    call.handleMarkNotificationRead()
                }
            }
        }
    }
}