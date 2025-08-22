package app.mcorg.pipeline.world

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.HandleGetWorldFailure
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.mockdata.MockUsers
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

data class WorldSettingsData(
    val world: World,
    val invitations: List<Invite>
)

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
    executePipeline(
        onSuccess = { data: WorldSettingsData ->
            val tabData = SettingsTab.Members(data.world, data.invitations, MockUsers.getWorldMembers())
            respondHtml(createHTML().div {
                membersTab(tabData)
            })
        },
        onFailure = { failure: HandleGetWorldFailure ->
            handleWorldFailure(failure)
        }
    ) {
        step(Step.value(worldId))
            .step(GetWorldAndInvitationsStep)
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

object GetWorldAndInvitationsStep : Step<Int, HandleGetWorldFailure, WorldSettingsData> {
    override suspend fun process(input: Int): Result<HandleGetWorldFailure, WorldSettingsData> {
        val worldId = input

        // Get world data
        val worldResult = worldQueryStep.process(worldId)
        if (worldResult is Result.Failure) {
            return worldResult
        }

        // Get invitations data
        val invitationsResult = GetWorldInvitationsStep.process(worldId)
        if (invitationsResult is Result.Failure) {
            return invitationsResult
        }

        return Result.success(WorldSettingsData(
            world = worldResult.getOrNull()!!,
            invitations = invitationsResult.getOrNull()!!
        ))
    }
}