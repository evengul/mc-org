package app.mcorg.presentation.handler

import app.mcorg.pipeline.invitation.commonsteps.GetUserInvitationsStep
import app.mcorg.pipeline.minecraftfiles.GetSupportedVersionsStep
import app.mcorg.pipeline.project.viewpreference.handleSetViewPreference
import app.mcorg.pipeline.project.handleCreateProject
import app.mcorg.pipeline.project.handleCreateProjectFromSchematic
import app.mcorg.pipeline.project.handleDeleteProject
import app.mcorg.pipeline.project.handleGetProject
import app.mcorg.pipeline.project.handleGetDetailContent
import app.mcorg.pipeline.project.resources.handleAddResourcesFromSchematic
import app.mcorg.pipeline.resources.handleClearOverride
import app.mcorg.pipeline.resources.handleGetDrillChain
import app.mcorg.pipeline.resources.handleGetNodePicker
import app.mcorg.pipeline.resources.handlePinSource
import app.mcorg.pipeline.resources.handleResolveTagMember
import app.mcorg.pipeline.resources.handleUpdatePlanProgress
import app.mcorg.pipeline.resources.handleClearResourceSource
import app.mcorg.pipeline.resources.handleCreateResourceGatheringItem
import app.mcorg.pipeline.resources.handleDeleteResourceGatheringItem
import app.mcorg.pipeline.resources.handleGetResourceDetailPanel
import app.mcorg.pipeline.resources.handleSetResourceSource
import app.mcorg.pipeline.resources.handleUpdateResourceRequiredAmount
import app.mcorg.pipeline.resources.handleSetCollectedValue
import app.mcorg.pipeline.resources.handleUpdateRequirementProgress
import app.mcorg.pipeline.task.handleCompleteActionTask
import app.mcorg.pipeline.task.handleCreateActionTask
import app.mcorg.pipeline.task.handleDeleteActionTask
import app.mcorg.pipeline.project.handleGetProjectList
import app.mcorg.pipeline.project.handleGetProjectListFragment
import app.mcorg.pipeline.project.handleGetFieldLogRow
import app.mcorg.pipeline.project.handleGetFieldLogSliceItems
import app.mcorg.pipeline.project.handleGetResumeRows
import app.mcorg.pipeline.project.handleUpdateProjectState
import app.mcorg.pipeline.project.handleGetProjectNameField
import app.mcorg.pipeline.project.handleUpdateProjectName
import app.mcorg.pipeline.project.handleGetProjectStateField
import app.mcorg.pipeline.project.handleUpdateProjectStateInline
import app.mcorg.pipeline.project.handleGetProjectLocationField
import app.mcorg.pipeline.project.handleUpdateProjectLocation
import app.mcorg.pipeline.world.handleCreateWorld
import app.mcorg.pipeline.world.handleDeleteWorld
import app.mcorg.pipeline.world.handleSearchWorlds
import app.mcorg.pipeline.world.settings.general.handleUpdateWorldDescription
import app.mcorg.pipeline.world.settings.general.handleUpdateWorldName
import app.mcorg.pipeline.world.settings.general.handleUpdateWorldVersion
import app.mcorg.pipeline.world.settings.handleConnectDiscord
import app.mcorg.pipeline.world.settings.handleDisconnectDiscord
import app.mcorg.pipeline.world.settings.handleGetWorldSettings
import app.mcorg.pipeline.world.settings.invitations.handleCancelInvitation
import app.mcorg.pipeline.world.settings.invitations.handleCreateInvitation
import app.mcorg.pipeline.world.settings.invitations.handleGetInvitationListFragment
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsInput
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsStep
import app.mcorg.pipeline.world.settings.members.handleRemoveWorldMember
import app.mcorg.pipeline.world.settings.members.handleUpdateWorldMemberRole
import app.mcorg.presentation.plugins.ActionTaskParamPlugin
import app.mcorg.presentation.plugins.InviteParamPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.ResourceGatheringIdParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import app.mcorg.presentation.plugins.WorldMemberParamPlugin
import app.mcorg.presentation.plugins.WorldOwnerPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.templated.dsl.pages.worldListPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.method
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

class WorldHandler {
    fun Route.worldRoutes() {
        route("/worlds") {
            get {
                call.handleGetHome()
            }
            post {
                call.handleCreateWorld()
            }
            get("/search") {
                call.handleSearchWorlds()
            }
            route("/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                get {
                    val worldId = call.parameters["worldId"]!!.toInt()
                    call.respondRedirect("/worlds/$worldId/projects", permanent = true)
                }
                route("/projects") {
                    get {
                        call.handleGetProjectList()
                    }
                    get("/list-fragment") {
                        call.handleGetProjectListFragment()
                    }
                    get("/resume-rows") {
                        call.handleGetResumeRows()
                    }
                    post {
                        call.handleCreateProject()
                    }
                    post("/from-schematic") {
                        call.handleCreateProjectFromSchematic()
                    }
                    route("/{projectId}") {
                        install(ProjectParamPlugin)
                        get {
                            call.handleGetProject()
                        }
                        get("/detail-content") {
                            call.handleGetDetailContent()
                        }
                        delete {
                            call.handleDeleteProject()
                        }
                        patch("/state") {
                            call.handleUpdateProjectState()
                        }
                        route("/meta") {
                            get("/name") { call.handleGetProjectNameField() }
                            patch("/name") { call.handleUpdateProjectName() }
                            get("/state") { call.handleGetProjectStateField() }
                            patch("/state") { call.handleUpdateProjectStateInline() }
                            get("/location") { call.handleGetProjectLocationField() }
                            patch("/location") { call.handleUpdateProjectLocation() }
                        }
                        get("/field-log-row") {
                            call.handleGetFieldLogRow()
                        }
                        get("/field-log-slice-items") {
                            call.handleGetFieldLogSliceItems()
                        }
                        route("/resources") {
                            post("/from-schematic") {
                                call.handleAddResourcesFromSchematic()
                            }
                            route("/gathering") {
                                post {
                                    call.handleCreateResourceGatheringItem()
                                }
                                route("/{resourceGatheringId}") {
                                    install(ResourceGatheringIdParamPlugin)
                                    patch("/edit-done") {
                                        call.handleUpdateRequirementProgress()
                                    }
                                    patch("/required") {
                                        call.handleUpdateResourceRequiredAmount()
                                    }
                                    put("/collected") {
                                        call.handleSetCollectedValue()
                                    }
                                    get("/detail-panel") {
                                        call.handleGetResourceDetailPanel()
                                    }
                                    patch("/source") {
                                        call.handleSetResourceSource()
                                    }
                                    delete("/source") {
                                        call.handleClearResourceSource()
                                    }
                                    delete {
                                        call.handleDeleteResourceGatheringItem()
                                    }
                                }
                            }
                        }
                        route("/tasks") {
                            post {
                                call.handleCreateActionTask()
                            }
                            route("/{taskId}") {
                                install(ActionTaskParamPlugin)
                                patch("/complete") {
                                    call.handleCompleteActionTask()
                                }
                                delete {
                                    call.handleDeleteActionTask()
                                }
                            }
                        }
                        post("/view-preference") {
                            call.handleSetViewPreference()
                        }
                        route("/plan") {
                            patch("/progress") {
                                call.handleUpdatePlanProgress()
                            }
                            route("/chain/{itemId}") {
                                get { call.handleGetDrillChain() }
                                get("/sources") { call.handleGetNodePicker() }
                                post("/pin") { call.handlePinSource() }
                                post("/tag") { call.handleResolveTagMember() }
                                delete("/override") { call.handleClearOverride() }
                            }
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
                    method(HttpMethod.Delete) {
                        install(WorldOwnerPlugin)
                        handle {
                            call.handleDeleteWorld()
                        }
                    }
                    route("/discord") {
                        post {
                            call.handleConnectDiscord()
                        }
                        delete("/{subscriptionId}") {
                            call.handleDisconnectDiscord()
                        }
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

    private suspend fun ApplicationCall.handleGetHome() {
        val user = getUser()
        val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()

        handlePipeline(
            onSuccess = { (worlds, invitations) ->
                respondHtml(worldListPage(user, worlds, supportedVersions, invitations))
            }
        ) {
            val worlds = GetPermittedWorldsStep.run(GetPermittedWorldsInput(userId = user.id))
            val invitations = GetUserInvitationsStep.run(user.id)
            worlds to invitations
        }
    }
}