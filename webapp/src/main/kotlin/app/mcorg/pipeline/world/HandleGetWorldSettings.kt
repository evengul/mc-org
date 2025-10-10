package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.HandleGetWorldFailure
import app.mcorg.pipeline.world.settings.handleGetInvitationListFragment
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.settings.SettingsTab
import app.mcorg.presentation.templated.settings.generalTab
import app.mcorg.presentation.templated.settings.membersTab
import app.mcorg.presentation.templated.settings.worldSettingsPage
import app.mcorg.presentation.templated.settings.statisticsTab
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetWorldSettings() {
    val user = this.getUser()
    val worldId = this.getWorldId()

    val isHtmxRequest = request.headers["HX-Request"] == "true"

    val tab = request.queryParameters["tab"]?.let {
        when(it) {
            "general", "members", "statistics" -> it
            else -> null // Invalid tab, return null
        }
    }

    val status = request.queryParameters["status"]?.let {
        when(it) {
            "all" -> GetWorldInvitationsInput.StatusFilter.ALL
            "pending" -> GetWorldInvitationsInput.StatusFilter.PENDING
            "accepted" -> GetWorldInvitationsInput.StatusFilter.ACCEPTED
            "declined" -> GetWorldInvitationsInput.StatusFilter.DECLINED
            "expired" -> GetWorldInvitationsInput.StatusFilter.EXPIRED
            "cancelled" -> GetWorldInvitationsInput.StatusFilter.CANCELLED
            else -> null
        }
    }

    if (tab == null && isHtmxRequest && status != null) {
        handleGetInvitationListFragment()
        return
    }

    val tabData = when(tab) {
        "members" -> handleGetMembersTabData(worldId, status ?: GetWorldInvitationsInput.StatusFilter.PENDING)
        "statistics" -> handleGetStatisticsTabData(worldId)
        else -> handleGetGeneralTabData(worldId) // General tab or no tab specified
    }

    if (tabData is Result.Failure) {
        handleWorldFailure(tabData.error)
        return
    }

    val tabDataResult = (tabData as Result.Success).value

    if (request.headers["HX-Request"] == "true") {
        // For HTMX requests, handle specific tabs
        respondHtml(
            createHTML().div {
                classes += "world-settings-content"
                when (tabDataResult) {
                    is SettingsTab.General -> generalTab(tabDataResult)
                    is SettingsTab.Members -> membersTab(tabDataResult)
                    is SettingsTab.Statistics -> statisticsTab(tabDataResult)
                }
            }
        )
    } else {
        respondHtml(worldSettingsPage(user, tabDataResult))
    }
}

suspend fun ApplicationCall.handleGetMembersTabData(worldId: Int, statusFilter: GetWorldInvitationsInput.StatusFilter): Result<HandleGetWorldFailure, SettingsTab.Members> {
    val worldPipeline = Pipeline.create<HandleGetWorldFailure, Int>()
        .pipe(Step.value(worldId))
        .pipe(worldQueryStep)

    val invitationsPipeline = Pipeline.create<HandleGetWorldFailure, Int>()
        .pipe(Step.value(GetWorldInvitationsInput(worldId, statusFilter)))
        .pipe(GetWorldInvitationsStep)

    val membersPipeline = Pipeline.create<HandleGetWorldFailure, Int>()
        .pipe(Step.value(worldId))
        .pipe(worldMembersQueryStep)

    return executeParallelPipeline {
        val world = pipeline("world", worldId, worldPipeline)
        val invitations = pipeline("invitations", worldId, invitationsPipeline)
        val members = pipeline("members", worldId, membersPipeline)

        merge("data", world, invitations, members) { w, i, m ->
            Result.success(SettingsTab.Members(
                world = w,
                invitations = i.filteredInvitations,
                invitationCounts = i.invitationsCount,
                members = m
            ))
        }
    }
}

suspend fun ApplicationCall.handleGetGeneralTabData(worldId: Int): Result<HandleGetWorldFailure, SettingsTab.General> {
    return executeParallelPipeline {
        pipeline(
            id = "world-general-tab",
            input = parameters,
            pipeline = Pipeline.create<HandleGetWorldFailure, Parameters>()
                .pipe(Step.value(worldId))
                .pipe(worldQueryStep)
                .map { SettingsTab.General(it) }
        )
    }
}

suspend fun ApplicationCall.handleGetStatisticsTabData(worldId: Int): Result<HandleGetWorldFailure, SettingsTab.Statistics> {
    return executeParallelPipeline {
        pipeline(
            id = "world-statistics-tab",
            input = parameters,
            pipeline = Pipeline.create<HandleGetWorldFailure, Parameters>()
                .pipe(Step.value(worldId))
                .pipe(worldQueryStep)
                .map { SettingsTab.Statistics(it) }
        )
    }
}
