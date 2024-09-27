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
                post {
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
                        post {
                            call.handlePostUser()
                        }
                    }
                    route("/projects") {
                        get {
                            call.handleGetProjects()
                        }
                        post {
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
                            patch("/assign") {
                                call.handlePatchProjectAssignee()
                            }
                            route("/tasks") {
                                post("/doable") {
                                    call.handlePostDoableTask()
                                }
                                post("/countable") {
                                    call.handlePostCountableTask()
                                }
                                post("/litematica") {
                                    call.handlePostLitematicaTasks()
                                }
                                patch("/requirements") {
                                    call.handleEditTaskRequirements()
                                }
                            }
                            route("/tasks/{taskId}") {
                                install(TaskParamPlugin)
                                delete {
                                    call.handleDeleteTask()
                                }
                                patch("/assign") {
                                    call.handlePatchTaskAssignee()
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
