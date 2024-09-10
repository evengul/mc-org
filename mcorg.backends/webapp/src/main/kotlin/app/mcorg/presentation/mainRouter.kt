package app.mcorg.presentation

import app.mcorg.presentation.handler.*
import app.mcorg.presentation.plugins.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureAppRouter() {
    routing {
        install(EnvPlugin)
        get {
            call.handleGetLanding()
        }
        route("/auth") {
            get("/sign-in") {
                call.handleGetSignIn()
            }
            get("/sign-out") {
                call.handleGetSignOut()
            }
            delete("/user") {
                call.handleDeleteUser()
            }
            get("/oidc/microsoft-redirect") {
                call.handleSignIn()
            }
            get("/oidc/local-redirect") {
                call.handleLocalSignIn()
            }
        }
        route("/app") {
            install(AuthPlugin)
            route("/profile") {
                get {
                    call.handleGetProfile()
                }
                patch("/photo") {
                    call.handleUploadProfilePhoto()
                }
                patch("/is-technical-player") {
                    call.handleIsTechnical()
                }
            }
            route("/worlds") {
                get {
                    call.handleGetWorlds()
                }
                get("/add") {
                    call.handleGetAddWorld()
                }
                post("/add") {
                    call.handlePostWorld()
                }
                patch("/select") {
                    call.handleSelectWorld()
                }
                route("/{worldId}") {
                    install(WorldParamPlugin)
                    install(WorldParticipantPlugin)
                    get {
                        call.handleGetWorld()
                    }
                    delete {
                        call.handleDeleteWorld()
                    }
                    route("/users") {
                        get {
                            call.handleGetUsers()
                        }
                        delete("/{userId}") {
                            call.handleDeleteWorldUser()
                        }
                        route("/add") {
                            install(WorldAdminPlugin)
                            get {
                                call.handleGetAddUser()
                            }
                            post {
                                call.handlePostUser()
                            }
                        }
                    }
                    route("/projects") {
                        get {
                            call.handleGetProjects()
                        }
                        get("/add") {
                            call.handleGetAddProject()
                        }
                        post("/add") {
                            call.handlePostProject()
                        }
                        route("/{projectId}") {
                            install(ProjectParamPlugin)
                            get {
                                call.handleGetProject()
                            }
                            delete {
                                call.handleDeleteProject()
                            }
                            get("/assign") {
                                call.handleGetAssignProject()
                            }
                            patch("/assign") {
                                call.handlePatchProjectAssignee()
                            }
                            delete("/assign") {
                                call.handleDeleteProjectAssignee()
                            }
                            route("/add-task") {
                                get {
                                    call.handleGetAddTask()
                                }
                                get("/doable") {
                                    call.handleGetAddDoableTask()
                                }
                                post("/doable") {
                                    call.handlePostDoableTask()
                                }
                                get("/countable") {
                                    call.handleGetAddCountableTask()
                                }
                                post("/countable") {
                                    call.handlePostCountableTask()
                                }
                                get("/litematica") {
                                    call.handleGetUploadLitematicaTasks()
                                }
                                post("/litematica") {
                                    call.handlePostLitematicaTasks()
                                }
                            }
                            patch("/tasks/requirements") {
                                call.handleEditTaskRequirements()
                            }
                            route("/tasks/{taskId}") {
                                install(TaskParamPlugin)
                                delete {
                                    call.handleDeleteTask()
                                }
                                get("/assign") {
                                    call.handleGetAssignTask()
                                }
                                patch("/assign") {
                                    call.handlePatchTaskAssignee()
                                }
                                delete("/assign") {
                                    call.handleDeleteTaskAssignee()
                                }
                                patch("/complete") {
                                    call.handleCompleteTask()
                                }
                                patch("/incomplete") {
                                    call.handleIncompleteTask()
                                }
                                patch("/do-more") {
                                    call.handlePatchCountableTaskDoneMore()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
