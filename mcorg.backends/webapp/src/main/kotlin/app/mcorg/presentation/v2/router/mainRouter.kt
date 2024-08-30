package app.mcorg.presentation.v2.router

import app.mcorg.presentation.v2.handler.*
import app.mcorg.presentation.v2.router.plugins.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureAppRouter() {
    routing {
        install(EnvPlugin)
        get {
            call.handleGetLanding()
        }
        route("/auth") {
            get("/signin") {
                call.handleGetSignIn()
            }
            get("/signout") {
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
                    get {
                        call.respondRedirect("/worlds/${call.parameters["worldId"]}/projects")
                    }
                    route("/users") {
                        get {
                            call.handleGetUsers()
                        }
                        get("/add") {
                            call.handleGetAddUser()
                        }
                        post("/add") {
                            call.handlePostUser()
                        }
                    }
                    route("/projects") {
                        get {
                            call.handleGetProjects()
                        }
                        get("/add") {
                            call.handleGetAddProject()
                        }
                        post {
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
                            post("/assign") {
                                call.handlePostProjectAssignee()
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
                            route("/tasks") {
                                get("/assign") {
                                    call.handleGetAssignTask()
                                }
                                patch("/assign") {
                                    call.handlePatchTaskAssignee()
                                }
                                route("/{taskId}") {
                                    install(TaskParamPlugin)
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
}
