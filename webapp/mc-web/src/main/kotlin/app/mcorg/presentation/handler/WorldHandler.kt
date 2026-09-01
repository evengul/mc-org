package app.mcorg.presentation.handler

import app.mcorg.pipeline.invitation.commonsteps.GetUserInvitationsStep
import app.mcorg.pipeline.minecraftfiles.GetSupportedVersionsStep
import app.mcorg.pipeline.project.viewpreference.handleSetViewPreference
import app.mcorg.pipeline.project.handleCreateProject
import app.mcorg.pipeline.project.handleCreateProjectFromSchematic
import app.mcorg.pipeline.project.handleReviewSchematic
import app.mcorg.pipeline.project.handleDeleteProject
import app.mcorg.pipeline.project.handleGetProject
import app.mcorg.pipeline.project.handleGetDetailContent
import app.mcorg.pipeline.world.roadmap.handleClearRoadmapCycleOrder
import app.mcorg.pipeline.world.roadmap.handleGetWorldRoadmap
import app.mcorg.pipeline.world.roadmap.handleSaveRoadmapCycleOrder
import app.mcorg.pipeline.project.handleRecordExistingFarm
import app.mcorg.pipeline.project.resources.handleAddResourcesFromSchematic
import app.mcorg.pipeline.project.resources.handleDeleteProjectProduction
import app.mcorg.pipeline.project.resources.handleGetProductionsPanel
import app.mcorg.pipeline.project.resources.handleUpsertProjectProduction
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
import app.mcorg.pipeline.resources.handleSwapResourceGatheringVariant
import app.mcorg.pipeline.resources.handleToggleResourceGatheringIgnored
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
import app.mcorg.pipeline.project.handleStartFarmSuggestionImport
import app.mcorg.pipeline.project.handleUpdateProjectState
import app.mcorg.pipeline.project.handleGetProjectNameField
import app.mcorg.pipeline.project.handleUpdateProjectName
import app.mcorg.pipeline.project.handleGetProjectStateField
import app.mcorg.pipeline.project.handleUpdateProjectStateInline
import app.mcorg.pipeline.project.handleGetProjectLocationField
import app.mcorg.pipeline.project.handleUpdateProjectLocation
import app.mcorg.pipeline.world.handleCreateWorld
import app.mcorg.pipeline.world.handleDeleteWorld
import app.mcorg.pipeline.world.handleTogglePin
import app.mcorg.pipeline.world.settings.general.handleUpdateWorldDescription
import app.mcorg.pipeline.world.settings.general.handleUpdateFarmScaleThreshold
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
import app.mcorg.pipeline.world.commonsteps.GetWorldProjectPeekStep
import app.mcorg.pipeline.world.settings.members.handleRemoveWorldMember
import app.mcorg.pipeline.world.settings.members.handleUpdateWorldMemberRole
import app.mcorg.presentation.plugins.ActionTaskParamPlugin
import app.mcorg.presentation.plugins.InviteParamPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.ProjectProductionItemParamPlugin
import app.mcorg.presentation.plugins.ResourceGatheringIdParamPlugin
import app.mcorg.presentation.plugins.SchematicUploadLimitPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import app.mcorg.presentation.plugins.WorldMemberParamPlugin
import app.mcorg.presentation.plugins.WorldOwnerPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
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
            // Pin toggle lives on its own /pin/{worldId} subtree (static `pin` beats the
            // `{worldId}` param branch) so it does NOT inherit UpdateActiveWorldPlugin —
            // pinning a world must not hijack the user's active world. Still membership-gated.
            route("/pin/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                post {
                    call.handleTogglePin()
                }
            }
            route("/{worldId}") {
                install(WorldParamPlugin)
                // World-membership gate (MCO-247): WorldParamPlugin above only checks the
                // world *exists*, not that the caller belongs to it — closing that IDOR gap
                // for the entire /{worldId} subtree (projects, resources, tasks, plan, meta,
                // field-log, view-preference). Must run before UpdateActiveWorldPlugin so a
                // non-member's session never gets its activeWorldId cookie pointed at a world
                // they can't access. /settings below is already WorldAdmin/Owner-gated; this
                // member check is just a harmless, correct precondition for it too.
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                get {
                    val worldId = call.parameters["worldId"]!!.toInt()
                    call.respondRedirect("/worlds/$worldId/projects", permanent = true)
                }
                get("/roadmap") {
                    call.handleGetWorldRoadmap()
                }
                // Which of two mutually-supplying projects comes first (MCO-460). Admin-only:
                // it sequences the world's projects for everyone who opens the roadmap, not
                // just the viewer, so it is a world decision like the farm-scale threshold.
                route("/roadmap/cycle-order") {
                    install(WorldAdminPlugin)
                    post {
                        call.handleSaveRoadmapCycleOrder()
                    }
                    post("/clear") {
                        call.handleClearRoadmapCycleOrder()
                    }
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
                    // Grouped so the upload cap runs as a plugin, ahead of the body read and
                    // ahead of the Role.ADMIN check these two do inside their pipelines — an
                    // unauthorized member could otherwise make the server buffer the file first
                    // and only then be refused (MCO-345). The URLs are unchanged.
                    route("/from-schematic") {
                        install(SchematicUploadLimitPlugin)
                        post("/review") {
                            call.handleReviewSchematic()
                        }
                        post {
                            call.handleCreateProjectFromSchematic()
                        }
                    }
                    post("/farm") {
                        call.handleRecordExistingFarm()
                    }
                    route("/{projectId}") {
                        install(ProjectParamPlugin)
                        get {
                            call.handleGetProject()
                        }
                        get("/detail-content") {
                            call.handleGetDetailContent()
                        }
                        method(HttpMethod.Delete) {
                            install(WorldAdminPlugin)
                            handle {
                                call.handleDeleteProject()
                            }
                        }
                        patch("/state") {
                            call.handleUpdateProjectState()
                        }
                        // Opens the batch review wizard for the designs ticked on the plan
                        // (MCO-459). Creates nothing itself — see the handler.
                        post("/farm-suggestions/import") {
                            call.handleStartFarmSuggestionImport()
                        }
                        route("/meta") {
                            get("/name") { call.handleGetProjectNameField() }
                            patch("/name") { call.handleUpdateProjectName() }
                            get("/state") { call.handleGetProjectStateField() }
                            patch("/state") { call.handleUpdateProjectStateInline() }
                            get("/location") { call.handleGetProjectLocationField() }
                            patch("/location") { call.handleUpdateProjectLocation() }
                        }
                        route("/productions") {
                            get("/panel") {
                                call.handleGetProductionsPanel()
                            }
                            post {
                                call.handleUpsertProjectProduction()
                            }
                            route("/{productionId}") {
                                install(ProjectProductionItemParamPlugin)
                                delete {
                                    call.handleDeleteProjectProduction()
                                }
                            }
                        }
                        get("/field-log-row") {
                            call.handleGetFieldLogRow()
                        }
                        get("/field-log-slice-items") {
                            call.handleGetFieldLogSliceItems()
                        }
                        route("/resources") {
                            // World-membership gate (MCO-247) now lives at the /{worldId}
                            // level above, covering this whole resource-mutation family too.
                            route("/from-schematic") {
                                install(SchematicUploadLimitPlugin)
                                post {
                                    call.handleAddResourcesFromSchematic()
                                }
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
                                    patch("/ignore") {
                                        call.handleToggleResourceGatheringIgnored()
                                    }
                                    patch("/variant") {
                                        call.handleSwapResourceGatheringVariant()
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
                    patch("/farm-scale-threshold") {
                        call.handleUpdateFarmScaleThreshold()
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
            onSuccess = { (worlds, invitations, heroPeek) ->
                respondHtml(worldListPage(user, worlds, supportedVersions, invitations, heroPeek))
            }
        ) {
            val worlds = GetPermittedWorldsStep.run(GetPermittedWorldsInput(userId = user.id))
            val invitations = GetUserInvitationsStep.run(user.id)
            val heroPeek = worlds.firstOrNull()
                ?.let { GetWorldProjectPeekStep().run(it.id) }
                ?: emptyList()
            Triple(worlds, invitations, heroPeek)
        }
    }
}