package app.mcorg.presentation.templated.dsl

import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div

data class TabItem(
    val value: String,
    val label: String,
    val href: String,
)

enum class TabVariant {
    DEFAULT, PILLS
}

fun FlowContent.tabStrip(
    tabs: List<TabItem>,
    activeValue: String,
    hxTarget: String,
    variant: TabVariant = TabVariant.DEFAULT,
    queryName: String = "tab",
) {
    div {
        classes = buildSet {
            add("tabs")
            if (variant == TabVariant.PILLS) add("tabs--pills")
        }
        attributes["role"] = "tablist"

        tabs.forEach { tab ->
            button {
                classes = buildSet {
                    add("tabs__tab")
                    if (tab.value == activeValue) add("tabs__tab--active")
                    add("tabs__tab--$queryName")
                }
                attributes["role"] = "tab"
                attributes["aria-selected"] = (tab.value == activeValue).toString()
                type = ButtonType.button

                hxGet(tab.href)
                hxTarget(hxTarget)
                hxSwap("outerHTML")
                attributes["hx-push-url"] = tab.href

                attributes["onclick"] = """
                    document.querySelectorAll('.tabs__tab--$queryName.tabs__tab--active').forEach(function(el){ el.classList.remove('tabs__tab--active'); el.setAttribute('aria-selected', 'false'); });
                    this.classList.add('tabs__tab--active');
                    this.setAttribute('aria-selected', 'true');
                """.trimIndent()

                +tab.label
            }
        }
    }
}
