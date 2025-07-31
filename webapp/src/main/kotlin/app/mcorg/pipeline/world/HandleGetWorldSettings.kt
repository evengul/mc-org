package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.pipeline.failure.HandleGetWorldFailure
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.mockdata.MockInvitations
import app.mcorg.presentation.mockdata.MockUsers
import app.mcorg.presentation.templated.settings.SettingsTab
import app.mcorg.presentation.templated.settings.generalTab
import app.mcorg.presentation.templated.settings.membersTab
import app.mcorg.presentation.templated.settings.worldSettingsPage
import app.mcorg.presentation.templated.settings.statisticsTab
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetWorldSettings() {
    val user = MockUsers.Evegul.tokenProfile()

    val tab = request.queryParameters["tab"]?.let {
        when(it) {
            "general", "members", "statistics" -> it
            else -> null // Invalid tab, return null
        }
    }

    executeParallelPipeline(
        onSuccess = { world ->
            if (request.headers["HX-Request"] == "true" && tab != null) {
                val tabData = when(tab) {
                    "members" -> SettingsTab.Members(world, MockInvitations.getByWorldId(world.id), MockUsers.getWorldMembers())
                    "statistics" -> SettingsTab.Statistics(world)
                    else -> SettingsTab.General(world)
                }
                handleGetTab(tabData)
            } else {
                respondHtml(worldSettingsPage(user, SettingsTab.General(world)))
            }
        },
        onFailure = { handleWorldFailure(it) }
    ) {
        pipeline(
            id = "world-settings",
            input = parameters,
            pipeline = Pipeline.create<HandleGetWorldFailure, Parameters>()
                .pipe(getWorldIdStep)
                .pipe(worldQueryStep)
                .pipe(validateWorldExistsStep)
        )
    }
}

suspend fun ApplicationCall.handleGetTab(tabData: SettingsTab) {
    respondHtml(createHTML().div {
        when(tabData) {
            is SettingsTab.General -> generalTab(tabData)
            is SettingsTab.Members -> membersTab(tabData)
            is SettingsTab.Statistics -> statisticsTab(tabData)
        }
    })
}