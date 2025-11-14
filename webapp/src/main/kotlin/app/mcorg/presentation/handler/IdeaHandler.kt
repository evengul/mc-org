package app.mcorg.presentation.handler

import app.mcorg.pipeline.idea.*
import app.mcorg.pipeline.idea.createfragments.handleGetAuthorFields
import app.mcorg.pipeline.idea.createfragments.handleGetCreateCategoryFields
import app.mcorg.pipeline.idea.createfragments.handleGetVersionFields
import app.mcorg.pipeline.idea.single.*
import app.mcorg.presentation.plugins.IdeaCommentParamPlugin
import app.mcorg.presentation.plugins.IdeaCreatorPlugin
import app.mcorg.presentation.plugins.IdeaParamPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import io.ktor.server.routing.*

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
                get {
                    call.handleGetCreateIdeaPage()
                }
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
                post("/import") {
                    route("/{worldId}") {
                        install(WorldParamPlugin)
                        post {

                        }
                    }
                    // Import idea into a world
                }
                put("/favourite") {
                    call.handleFavouriteIdea()
                }
                patch("/public") {
                    // Make idea public
                }
                patch("/archive") {
                    // Archive idea
                }
                delete {
                    call.handleDeleteIdea()
                }

                route("/comments") {
                    post {
                        call.handleCreateIdeaComment()
                    }
                    route("/{commentId}") {
                        install(IdeaCommentParamPlugin)
                        patch {
                            // Update comment
                        }
                        patch("/like") {
                            // Like comment
                        }
                        delete {
                            call.handleDeleteIdeaComment()
                        }
                    }
                }
            }
        }
    }
}