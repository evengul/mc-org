package app.mcorg.presentation.templated.testpage

import app.mcorg.presentation.templated.common.tabs.tabsComponent
import kotlinx.html.MAIN
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.summary

fun MAIN.testTabs() {
    details {
        summary { + "Tabs" }
        div("tab-row") {
            tabsComponent("Tab 1", "Tab 2", "Tab 3") {
                activeTab = "Tab 1"
            }
            tabsComponent("Tab 1", "Tab 2", "Tab 3")
        }
    }
}