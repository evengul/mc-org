package app.mcorg.presentation.templated.common.tabs

import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.*
import org.intellij.lang.annotations.Language

data class TabData(
    val value: String,
    val label: String
) {
    companion object {
        fun create(value: String, label: String): TabData {
            return TabData(value, label)
        }

        fun create(label: String): TabData {
            return TabData(value = label.lowercase().replace(" ", "-"), label = label)
        }
    }
}

enum class TabsVariant {
    DEFAULT, PILLS, UNDERLINED
}

enum class TabsSize {
    SMALL, MEDIUM, LARGE
}

fun <T : Tag> T.tabsComponent(
    vararg tabs: String,
    handler: (Tabs.() -> Unit)? = null,
) {
    val component = Tabs(
        tabs = tabs.map { TabData.create(it) }
    )
    handler?.invoke(component)
    addComponent(component)
}

fun <T : Tag> T.tabsComponent(
    vararg tabs: TabData,
    handler: (Tabs.() -> Unit)? = null,
) {
    tabsComponent(tabs.toList(), handler)
}

fun <T : Tag> T.tabsComponent(
    tabList: List<TabData>,
    handler: (Tabs.() -> Unit)? = null,
) {
    val component = Tabs(
        tabs = tabList
    )
    handler?.invoke(component)
    addComponent(component)
}

fun <T : Tag> T.tabsComponent(
    hxTarget: String,
    vararg tabs: TabData,
    handler: (Tabs.() -> Unit)? = null,
) {
    tabsComponent(hxTarget = hxTarget, tabList = tabs.toList(), handler = handler)
}

fun <T : Tag> T.tabsComponent(
    hxTarget: String,
    tabList: List<TabData>,
    handler: (Tabs.() -> Unit)? = null,
) {
    val component = Tabs(tabList, hxTarget)
    handler?.invoke(component)
    addComponent(component)
}

class Tabs(
    val tabs: List<TabData>,
    var hxTarget: String? = null,
    var hxSwap: String? = null,
    var activeTab: String? = null,
    var queryName: String = "tab",
    var variant: TabsVariant = TabsVariant.DEFAULT,
    var size: TabsSize = TabsSize.MEDIUM,
    var onClick: ((tab: TabData) -> String)? = null,
    var classes: MutableSet<String> = mutableSetOf(),
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        container.div {
            // Apply base tabs class and modifiers
            classes = this@Tabs.classes + mutableSetOf("tabs").apply {
                // Add variant modifier classes
                when (this@Tabs.variant) {
                    TabsVariant.DEFAULT -> { /* Default variant */ }
                    TabsVariant.PILLS -> add("tabs--pills")
                    TabsVariant.UNDERLINED -> add("tabs--underlined")
                }

                // Add size modifier classes
                when (this@Tabs.size) {
                    TabsSize.SMALL -> add("tabs--sm")
                    TabsSize.MEDIUM -> { /* Default size */ }
                    TabsSize.LARGE -> add("tabs--lg")
                }
            }

            // Add ARIA attributes for accessibility
            attributes["role"] = "tablist"

            tabs.forEach { tab ->
                button {
                    // Apply tab button classes using new CSS architecture
                    classes = mutableSetOf("tabs__tab").apply {
                        // Use neutral button styling as base for tabs
                        add("btn")
                        add("btn--ghost")

                        // Add size modifier to match tabs size
                        when (this@Tabs.size) {
                            TabsSize.SMALL -> add("btn--sm")
                            TabsSize.MEDIUM -> { /* Default size */ }
                            TabsSize.LARGE -> add("btn--lg")
                        }

                        // Add active state
                        if (tab.value == activeTab) {
                            add("tabs__tab--active")
                        }

                        add("tabs__tab--${queryName}")
                    }

                    @Language("JavaScript")
                    val mainClick = """
                        document.querySelectorAll('.tabs__tab--${queryName}.tabs__tab--active').forEach(el => el.classList.remove('tabs__tab--active'));
                        this.classList.add('tabs__tab--active');
                        if (location.href.indexOf('?') > -1) {
                            const searchParams = new URLSearchParams(location.search);
                            searchParams.set('${queryName}', '${tab.value}');
                            history.replaceState(null, '', location.pathname + '?' + searchParams.toString());
                        } else {
                            history.replaceState(null, '', location.href + '?${queryName}=${tab.value}');
                        }
                    """.trimIndent()

                    this@Tabs.onClick?.let {
                        val customClick = it(tab)
                        onClick = mainClick + customClick
                    }

                    if (this@Tabs.onClick == null) {
                        onClick = mainClick
                    }

                    hxTarget?.let { target ->
                        hxTarget(target)
                        hxGet("?${queryName}=${tab.value}")
                        hxSwap(hxSwap ?: "outerHTML")
                    }

                    // Accessibility attributes
                    attributes["role"] = "tab"
                    attributes["aria-selected"] = (tab.value == activeTab).toString()
                    attributes["aria-controls"] = "tabpanel-${tab.value}"

                    type = ButtonType.button

                    + tab.label
                }
            }
        }
    }
}

// Convenience functions for common tab variants
fun <T : Tag> T.pillTabs(
    vararg tabs: String,
    activeTab: String? = null,
    size: TabsSize = TabsSize.MEDIUM,
    handler: (Tabs.() -> Unit)? = null,
) {
    tabsComponent(*tabs) {
        this.variant = TabsVariant.PILLS
        this.activeTab = activeTab
        this.size = size
        handler?.invoke(this)
    }
}

fun <T : Tag> T.underlinedTabs(
    vararg tabs: String,
    activeTab: String? = null,
    size: TabsSize = TabsSize.MEDIUM,
    handler: (Tabs.() -> Unit)? = null,
) {
    tabsComponent(*tabs) {
        this.variant = TabsVariant.UNDERLINED
        this.activeTab = activeTab
        this.size = size
        handler?.invoke(this)
    }
}

fun <T : Tag> T.htmxTabs(
    hxTarget: String,
    vararg tabs: TabData,
    variant: TabsVariant = TabsVariant.DEFAULT,
    activeTab: String? = null,
    size: TabsSize = TabsSize.MEDIUM,
    handler: (Tabs.() -> Unit)? = null,
) {
    tabsComponent(hxTarget, *tabs) {
        this.variant = variant
        this.activeTab = activeTab
        this.size = size
        handler?.invoke(this)
    }
}