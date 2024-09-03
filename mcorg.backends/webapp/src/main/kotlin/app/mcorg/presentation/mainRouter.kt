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
                patch("/is-technical") {
                    call.handleIsTechnical()
                }
                patch("/is-not-technical") {
                    call.handleIsNotTechnical()
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
                route("/{worldId}") {
                    install(WorldParamPlugin)
                    install(WorldParticipantPlugin)
                    get {
                        call.handleGetWorld()
                    }
                    route("/users") {
                        get {
                            call.handleGetUsers()
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
                            get("/assign") {
                                call.handleGetAssignProject()
                            }
                            patch("/assign") {
                                call.handlePostProjectAssignee()
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
                            }
                            route("/tasks/{taskId}") {
                                install(TaskParamPlugin)
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
                            }
                        }
                    }
                }
            }
        }
    }
}
