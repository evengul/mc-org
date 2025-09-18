package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.HandleGetWorldFailure
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
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetWorldSettings() {
    val user = this.getUser()
    val worldId = this.getWorldId()

    val tab = request.queryParameters["tab"]?.let {
        when(it) {
            "general", "members", "statistics" -> it
            else -> null // Invalid tab, return null
        }
    }

    if (request.headers["HX-Request"] == "true" && tab != null) {
        // For HTMX requests, handle specific tabs
        when (tab) {
            "members" -> handleGetMembersTab(worldId)
            "statistics" -> handleGetStatisticsTab(worldId)
            else -> handleGetGeneralTab(worldId)
        }
    } else {
        // For full page requests, just show general tab
        executeParallelPipeline(
            onSuccess = { world ->
                respondHtml(worldSettingsPage(user, SettingsTab.General(world)))
            },
            onFailure = { handleWorldFailure(it) }
        ) {
            pipeline(
                id = "world-settings",
                input = parameters,
                pipeline = Pipeline.create<HandleGetWorldFailure, Parameters>()
                    .pipe(Step.value(worldId))
                    .pipe(worldQueryStep)
            )
        }
    }
}

suspend fun ApplicationCall.handleGetMembersTab(worldId: Int) {
    val worldPipeline = Pipeline.create<HandleGetWorldFailure, Int>()
        .pipe(Step.value(worldId))
        .pipe(worldQueryStep)

    val invitationsPipeline = Pipeline.create<HandleGetWorldFailure, Int>()
        .pipe(Step.value(worldId))
        .pipe(GetWorldInvitationsStep)

    val membersPipeline = Pipeline.create<HandleGetWorldFailure, Int>()
        .pipe(Step.value(worldId))
        .pipe(worldMembersQueryStep)

    executeParallelPipeline(
        onSuccess = { tabData -> respondHtml(createHTML().div {
            membersTab(tabData)
        })},
        onFailure = { handleWorldFailure(it) }
    ) {
        val world = pipeline("world", worldId, worldPipeline)
        val invitations = pipeline("invitations", worldId, invitationsPipeline)
        val members = pipeline("members", worldId, membersPipeline)

        merge("data", world, invitations, members) { w, i, m ->
            Result.success(SettingsTab.Members(
                world = w,
                invitations = i,
                members = m
            ))
        }
    }
}

suspend fun ApplicationCall.handleGetGeneralTab(worldId: Int) {
    executeParallelPipeline(
        onSuccess = { world ->
            val tabData = SettingsTab.General(world)
            respondHtml(createHTML().div {
                generalTab(tabData)
            })
        },
        onFailure = { handleWorldFailure(it) }
    ) {
        pipeline(
            id = "world-general-tab",
            input = parameters,
            pipeline = Pipeline.create<HandleGetWorldFailure, Parameters>()
                .pipe(Step.value(worldId))
                .pipe(worldQueryStep)
        )
    }
}

suspend fun ApplicationCall.handleGetStatisticsTab(worldId: Int) {
    executeParallelPipeline(
        onSuccess = { world ->
            val tabData = SettingsTab.Statistics(world)
            respondHtml(createHTML().div {
                statisticsTab(tabData)
            })
        },
        onFailure = { handleWorldFailure(it) }
    ) {
        pipeline(
            id = "world-statistics-tab",
            input = parameters,
            pipeline = Pipeline.create<HandleGetWorldFailure, Parameters>()
                .pipe(Step.value(worldId))
                .pipe(worldQueryStep)
        )
    }
}
