package app.mcorg.presentation.handler.v2

import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

class IdeaHandler {
    fun Route.ideaRoutes() {
        route("/ideas") {
            get {

            }
            post {

            }
            route("/{ideaId}") {
                get {

                }
                post("/import/{worldId}") {
                    // Import idea into a world
                }
                patch("/like") {
                    // Like idea
                }
                patch("/rate") {
                    // Rate idea
                }
                patch("/public") {
                    // Make idea public
                }
                patch("/archive") {
                    // Archive idea
                }
                delete {
                    // Delete idea
                }

                route("/comments") {
                    post {
                        // Add a comment to the idea
                    }
                    route("/{commentId}") {
                        patch {
                            // Update comment
                        }
                        patch("/like") {
                            // Like comment
                        }
                        delete {
                            // Delete comment
                        }
                    }
                }
            }
        }
    }
}