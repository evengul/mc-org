package app.mcorg.pipeline.world

import app.mcorg.presentation.mockdata.MockInvitations
import app.mcorg.presentation.mockdata.MockUsers
import app.mcorg.presentation.mockdata.MockWorlds
import app.mcorg.presentation.templated.settings.SettingsTab
import app.mcorg.presentation.templated.settings.generalTab
import app.mcorg.presentation.templated.settings.membersTab
import app.mcorg.presentation.templated.settings.settingsPage
import app.mcorg.presentation.templated.settings.statisticsTab
import app.mcorg.presentation.utils.respondHtml
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

    val world = parameters["worldId"]?.toIntOrNull()?.let { MockWorlds.getById(it) }
        ?: return respondHtml("World ID is required")

    val tabData = when(tab) {
        "members" -> SettingsTab.Members(world, MockInvitations.getByWorldId(world.id), MockUsers.getWorldMembers())
        "statistics" -> SettingsTab.Statistics(world)
        else -> SettingsTab.General(world)
    }

    if (request.headers["HX-Request"] == "true" && tab != null) {
        handleGetTab(tabData)
        return
    }

    respondHtml(settingsPage(user, tabData))
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