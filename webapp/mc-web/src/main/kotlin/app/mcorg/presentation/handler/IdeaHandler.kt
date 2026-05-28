package app.mcorg.presentation.handler

import app.mcorg.pipeline.idea.createfragments.handleGetAuthorFields
import app.mcorg.pipeline.idea.createfragments.handleGetCreateCategoryFields
import app.mcorg.pipeline.idea.createfragments.handleGetItemRequirementFields
import app.mcorg.pipeline.idea.createfragments.handleGetVersionFields
import app.mcorg.pipeline.idea.createfragments.handleParseLitematica
import app.mcorg.pipeline.idea.draft.handleCreateDraft
import app.mcorg.pipeline.idea.draft.handleDeleteDraft
import app.mcorg.pipeline.idea.draft.handleGetDraftList
import app.mcorg.pipeline.idea.draft.handleGetDraftWizard
import app.mcorg.pipeline.idea.draft.handlePublishDraft
import app.mcorg.pipeline.idea.draft.handleRevertIdeaToDraft
import app.mcorg.pipeline.idea.draft.handleUpdateDraftStage
import app.mcorg.pipeline.idea.handleClearCategoryFilters
import app.mcorg.pipeline.idea.handleGetCategoryFilters
import app.mcorg.pipeline.idea.handleGetIdeas
import app.mcorg.pipeline.idea.handleSearchIdeas
import app.mcorg.pipeline.idea.single.handleCreateIdeaComment
import app.mcorg.pipeline.idea.single.handleDeleteIdea
import app.mcorg.pipeline.idea.single.handleDeleteIdeaComment
import app.mcorg.pipeline.idea.single.handleFavouriteIdea
import app.mcorg.pipeline.idea.single.handleGetIdea
import app.mcorg.pipeline.project.handleGetSelectWorldForIdeaImportFragment
import app.mcorg.pipeline.project.handleImportIdea
import app.mcorg.presentation.plugins.IdeaCommentParamPlugin
import app.mcorg.presentation.plugins.IdeaCreatorPlugin
import app.mcorg.presentation.plugins.IdeaParamPlugin
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
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

            // Draft-based creation flow (replaces session-based wizard)
            route("/create") {
                install(IdeaCreatorPlugin)
                get {
                    call.handleGetDraftList()
                }
                post {
                    call.handleCreateDraft()
                }
                // Legacy fragment endpoints retained for now (used by old wizard fields)
                get("/fields/{category}") {
                    call.handleGetCreateCategoryFields()
                }
                get("/author-fields") {
                    call.handleGetAuthorFields()
                }
                get("/version-fields") {
                    call.handleGetVersionFields()
                }
                get("/item-requirement-field") {
                    call.handleGetItemRequirementFields()
                }
                post("/litematic") {
                    call.handleParseLitematica()
                }
            }

            // Draft wizard routes
            route("/drafts") {
                install(IdeaCreatorPlugin)
                route("/{draftId}") {
                    get("/edit") {
                        call.handleGetDraftWizard()
                    }
                    post("/stage") {
                        call.handleUpdateDraftStage()
                    }
                    post("/publish") {
                        call.handlePublishDraft()
                    }
                    delete {
                        call.handleDeleteDraft()
                    }
                }
            }

            route("/{ideaId}") {
                install(IdeaParamPlugin)
                get {
                    call.handleGetIdea()
                }
                route("/import") {
                    get("/select") {
                        call.handleGetSelectWorldForIdeaImportFragment()
                    }
                    post {
                        call.handleImportIdea()
                    }
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

                route("/revert") {
                    install(IdeaCreatorPlugin)
                    post {
                        call.handleRevertIdeaToDraft()
                    }
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
