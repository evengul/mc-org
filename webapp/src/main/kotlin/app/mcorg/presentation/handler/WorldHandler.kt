package app.mcorg.presentation.handler

import app.mcorg.pipeline.world.handleCreateWorld
import app.mcorg.pipeline.world.handleGetProject
import app.mcorg.pipeline.world.handleGetWorld
import app.mcorg.pipeline.world.handleGetWorldSettings
import app.mcorg.pipeline.project.handleCreateProject
import app.mcorg.pipeline.project.handleDeleteProject
import app.mcorg.pipeline.project.handleEditLocation
import app.mcorg.pipeline.project.handleGetEditLocationFragment
import app.mcorg.pipeline.project.handleUpdateProjectStage
import app.mcorg.pipeline.world.handleDeleteWorld
import app.mcorg.pipeline.world.handleUpdateWorld
import app.mcorg.pipeline.world.settings.handleCancelInvitation
import app.mcorg.pipeline.world.settings.handleCreateInvitation
import app.mcorg.pipeline.world.settings.handleGetInvitationListFragment
import app.mcorg.pipeline.world.settings.handleUpdateWorldName
import app.mcorg.pipeline.world.settings.handleUpdateWorldDescription
import app.mcorg.pipeline.world.settings.handleUpdateWorldVersion
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.TaskParamPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.handler.TaskHandler.handleCompleteTask
import app.mcorg.presentation.handler.TaskHandler.handleCreateTask
import app.mcorg.presentation.handler.TaskHandler.handleDeleteTask
import app.mcorg.presentation.handler.TaskHandler.handleSearchTasks
import app.mcorg.presentation.handler.TaskHandler.handleUpdateRequirementProgress
import app.mcorg.presentation.handler.TaskHandler.handleToggleActionRequirement
import app.mcorg.presentation.plugins.InviteParamPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

class WorldHandler {
    fun Route.worldRoutes() {
        route("/worlds") {
            post {
                call.handleCreateWorld()
            }
            route("/{worldId}") {
                install(WorldParamPlugin)
                get {
                    call.handleGetWorld()
                }
                put {
                    call.handleUpdateWorld()
                }
                route("/projects") {
                    post {
                        call.handleCreateProject()
                    }
                    route("/{projectId}") {
                        install(ProjectParamPlugin)
                        get {
                            call.handleGetProject()
                        }
                        patch("/stage") {
                            // Update project stage
                            call.handleUpdateProjectStage()
                        }
                        delete {
                            install(WorldAdminPlugin)
                            call.handleDeleteProject()
                        }
                        route("/resources") {
                            post {

                            }
                            route("/{resourceId}") {
                                patch("/active") {
                                    // Update resource
                                }
                                patch("/rate") {

                                }
                                delete {
                                    // Delete resource
                                }
                            }
                        }
                        route("/location") {
                            get("/edit") {
                                call.handleGetEditLocationFragment()
                            }
                            put {
                                call.handleEditLocation()
                            }
                        }
                        route("/dependencies") {
                            post("/{projectId}") {

                            }
                            delete("/{projectId}") {
                                // Remove project dependency
                            }
                            post("/{projectId}/task/{taskId}") {
                                // Add task dependency
                            }
                            delete("/{projectId}/task/{taskId}") {
                                // Remove task dependency
                            }
                        }
                        route("/tasks") {
                            post {
                                call.handleCreateTask()
                            }
                            get("/search") {
                                call.handleSearchTasks()
                            }
                            route("/{taskId}") {
                                install(TaskParamPlugin)
                                patch("/complete") {
                                    call.handleCompleteTask()
                                }
                                delete {
                                    call.handleDeleteTask()
                                }
                                route("/requirements") {
                                    put {
                                        // Update task requirements (edit functionality)
                                    }
                                    route("/{requirementId}") {
                                        patch("/done-more") {
                                            call.handleUpdateRequirementProgress()
                                        }
                                        patch("/toggle") {
                                            call.handleToggleActionRequirement()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                route("/resources") {
                    get {

                    }
                    route("/map") {
                        post {

                        }
                        delete {

                        }
                        post("/{mapId}") {
                            // Create map resource
                        }
                        delete("/{mapId}/{resourceId}") {
                            // Delete map resource
                        }
                    }
                }
                route("/settings") {
                    install(WorldAdminPlugin)
                    get {
                        call.handleGetWorldSettings()
                    }
                    patch("/name") {
                        call.handleUpdateWorldName()
                    }
                    patch("/description") {
                        call.handleUpdateWorldDescription()
                    }
                    patch("/version") {
                        call.handleUpdateWorldVersion()
                    }
                    delete {
                        call.handleDeleteWorld()
                    }
                    route("/members") {
                        route("/invitations") {
                            get {
                                call.handleGetInvitationListFragment()
                            }
                            post {
                                call.handleCreateInvitation()
                            }
                            route("/{inviteId}") {
                                install(InviteParamPlugin)
                                delete {
                                    call.handleCancelInvitation()
                                }
                            }
                        }
                        patch("/role") {
                            // Update member role
                        }
                        delete {
                            // Remove member from world
                        }
                    }
                }
            }
        }
    }
}