package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.button.backButton
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.common.tabs.TabData
import app.mcorg.presentation.templated.common.tabs.tabsComponent
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.p

sealed interface SettingsTab {
    val id: String
    val world: World

    data class General(override val world: World) : SettingsTab {
        override val id: String = "general"
    }

    data class Members(override val world: World, val invitations: List<Invite>, val members: List<WorldMember>) : SettingsTab {
        override val id: String = "members"
    }

    data class Statistics(override val world: World) : SettingsTab {
        override val id: String = "statistics"
    }
}

fun settingsPage(user: TokenProfile, tab: SettingsTab) = createPage("Settings", user = user) {
    classes += "world-settings-page"
    backButton("Back to ${tab.world.name}", Link.Worlds.world(tab.world.id))
    div {
        h1 {
            + "World Settings"
        }
        p("subtle") {
            + "Manage your world settings and members"
        }
    }
    tabsComponent(
        hxTarget = ".world-settings-content",
        TabData.create("General"),
        TabData.create("Members"),
        TabData.create("Statistics")
    ) {
        activeTab = tab.id
    }
    div("world-settings-content") {
        when(tab) {
            is SettingsTab.General -> generalTab(tab)
            is SettingsTab.Members -> membersTab(tab)
            is SettingsTab.Statistics -> statisticsTab(tab)
        }
    }
}