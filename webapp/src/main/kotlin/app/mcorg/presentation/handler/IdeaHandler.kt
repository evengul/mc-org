package app.mcorg.presentation.handler

import app.mcorg.pipeline.idea.*
import app.mcorg.pipeline.idea.single.handleGetIdea
import app.mcorg.presentation.plugins.IdeaCreatorPlugin
import app.mcorg.presentation.plugins.IdeaParamPlugin
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

            get("/search") {
                call.handleSearchIdeas()
            }

            route("/filters") {
                get("/clear") {
                    call.handleClearCategoryFilters()
                }
                get("/{category}") {
                    call.handleGetCategoryFilters()
                }
            }

            route("/create") {
                install(IdeaCreatorPlugin)
                get("/fields/{category}") {
                    call.handleGetCreateCategoryFields()
                }
                get("/author-fields") {
                    call.handleGetAuthorFields()
                }
                get("/version-fields") {
                    call.handleGetVersionFields()
                }
                post {
                    call.handleCreateIdea()
                }
            }
            route("/{ideaId}") {
                install(IdeaParamPlugin)
                get {
                    call.handleGetIdea()
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