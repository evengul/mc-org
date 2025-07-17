package app.mcorg.presentation.handler.v2

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route

class NotificationHandler {
    fun Route.notificationRoutes() {
        route("/notifications") {
            get {

            }
            patch("/{notificationId}/read") {
                // Mark notification as read
            }
            patch("/read") {
                // Mark all notifications as read
            }
        }
    }
}