package app.mcorg.presentation.handler

import app.mcorg.pipeline.idea.handleGetIdeas
import app.mcorg.pipeline.idea.handleSearchIdeas
import app.mcorg.pipeline.idea.handleGetCategoryFilters
import app.mcorg.pipeline.idea.handleClearCategoryFilters
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
                call.handleGetIdeas()
            }

            // HTMX endpoint: Search/filter ideas
            get("/search") {
                call.handleSearchIdeas()
            }

            // HTMX endpoint: Get category-specific filters
            route("/filters") {
                get("/clear") {
                    call.handleClearCategoryFilters()
                }
                get("/{category}") {
                    call.handleGetCategoryFilters()
                }
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