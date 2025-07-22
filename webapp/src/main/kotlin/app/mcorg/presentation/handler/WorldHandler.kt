package app.mcorg.presentation.handler

import app.mcorg.pipeline.world.handleCreateWorld
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
                get {

                }
                route("/projects") {
                    post {
                        // Accept, validate, and process project creation
                    }
                    route("/{projectId}") {
                        get {

                        }
                        patch("/stage") {
                            // Update project stage
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
                        patch("/location") {

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
                                // Create a new task
                            }
                            route("/{taskId}") {
                                patch("/complete") {
                                    // Mark task as complete
                                }
                                delete {
                                    // Delete task
                                }
                                route("/requirements") {
                                    put {

                                    }
                                    patch("/done-more") {
                                        // Update done count for a task
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
                        get {

                        }
                        patch("/name") {
                            // Update world name
                        }
                        patch("/description") {
                            // Update world description
                        }
                        patch("/version") {
                            // Update world version
                        }
                        delete {

                        }
                        route("/members") {
                            route("/invitations") {
                                post {
                                    // Create a new world invitation
                                }
                                delete("/{inviteId}") {

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
}