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
    /** URL fetched into [hxTarget] when the tab is clicked (typically a fragment endpoint). */
    val href: String,
    /**
     * Canonical URL pushed to the address bar. Defaults to [href]. Set this when [href]
     * is a fragment endpoint (which renders without the page shell), so reload/share lands
     * on the full page instead of a bare, CSS-less fragment.
     */
    val pushUrl: String? = null,
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
                attributes["hx-push-url"] = tab.pushUrl ?: tab.href

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
