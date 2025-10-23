package app.mcorg.presentation.handler

import app.mcorg.pipeline.project.dependencies.handleCreateProjectDependency
import app.mcorg.pipeline.project.dependencies.handleDeleteProjectDependency
import app.mcorg.pipeline.world.handleCreateWorld
import app.mcorg.pipeline.world.handleGetProject
import app.mcorg.pipeline.world.handleGetWorld
import app.mcorg.pipeline.world.handleGetWorldSettings
import app.mcorg.pipeline.project.handleCreateProject
import app.mcorg.pipeline.project.handleDeleteProject
import app.mcorg.pipeline.project.handleEditLocation
import app.mcorg.pipeline.project.handleGetEditLocationFragment
import app.mcorg.pipeline.project.handleUpdateProjectStage
import app.mcorg.pipeline.project.resources.handleCreateProjectProduction
import app.mcorg.pipeline.project.resources.handleDeleteProjectProductionItem
import app.mcorg.pipeline.project.settings.handleUpdateProjectDescription
import app.mcorg.pipeline.project.settings.handleUpdateProjectName
import app.mcorg.pipeline.project.settings.handleUpdateProjectType
import app.mcorg.pipeline.world.handleDeleteWorld
import app.mcorg.pipeline.world.handleSearchProjects
import app.mcorg.pipeline.world.handleSearchWorlds
import app.mcorg.pipeline.world.settings.handleCancelInvitation
import app.mcorg.pipeline.world.settings.handleCreateInvitation
import app.mcorg.pipeline.world.settings.handleGetInvitationListFragment
import app.mcorg.pipeline.world.settings.handleUpdateWorldName
import app.mcorg.pipeline.world.settings.handleUpdateWorldDescription
import app.mcorg.pipeline.world.settings.handleUpdateWorldVersion
import app.mcorg.pipeline.world.settings.members.handleRemoveWorldMember
import app.mcorg.pipeline.world.settings.members.handleUpdateWorldMemberRole
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
import app.mcorg.presentation.plugins.ProjectDependencyItemPlugin
import app.mcorg.presentation.plugins.ProjectProductionItemParamPlugin
import app.mcorg.presentation.plugins.WorldMemberParamPlugin
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
            get("/search") {
                call.handleSearchWorlds()
            }
            route("/{worldId}") {
                install(WorldParamPlugin)
                get {
                    call.handleGetWorld()
                }
                route("/projects") {
                    get("/search") {
                        call.handleSearchProjects()
                    }
                    post {
                        call.handleCreateProject()
                    }
                    route("/{projectId}") {
                        install(ProjectParamPlugin)
                        get {
                            call.handleGetProject()
                        }
                        patch("/stage") {
                            call.handleUpdateProjectStage()
                        }
                        put("/name") {
                            call.handleUpdateProjectName()
                        }
                        put("/description") {
                            call.handleUpdateProjectDescription()
                        }
                        put("/type") {
                            call.handleUpdateProjectType()
                        }
                        delete {
                            call.handleDeleteProject()
                        }
                        route("/resources") {
                            post {
                                call.handleCreateProjectProduction()
                            }
                            route("/{resourceId}") {
                                install(ProjectProductionItemParamPlugin)
                                patch("/active") {
                                    // Update resource
                                }
                                patch("/rate") {

                                }
                                delete {
                                    call.handleDeleteProjectProductionItem()
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
                            post {
                                call.handleCreateProjectDependency()
                            }
                            route("/{dependencyId}") {
                                install(ProjectDependencyItemPlugin)
                                delete {
                                    call.handleDeleteProjectDependency()
                                }
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
                        route("/{memberId}") {
                            install(WorldMemberParamPlugin)
                            patch("/role") {
                                call.handleUpdateWorldMemberRole()
                            }
                            delete {
                                call.handleRemoveWorldMember()
                            }
                        }
                    }
                }
            }
        }
    }
}