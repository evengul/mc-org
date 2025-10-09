package app.mcorg.presentation.handler

import app.mcorg.pipeline.admin.handleGetAdminPage
import app.mcorg.presentation.plugins.AdminPlugin
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route

class AdminHandler {
    fun Route.adminRoutes() {
        route("/admin") {
            install(AdminPlugin)
            get {
                call.handleGetAdminPage()
            }
            route("/users") {
                route("/{userId}") {
                    patch("/role") {
                        // Update user role
                    }
                    patch("/ban") {
                        // Ban user
                    }
                    delete {
                        // Delete user
                    }
                }
            }
            route("/worlds") {
                route("/{worldId}") {
                    delete {
                        // Delete world
                    }
                }
            }
        }
    }
}