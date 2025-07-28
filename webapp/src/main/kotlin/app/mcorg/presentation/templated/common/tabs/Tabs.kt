package app.mcorg.presentation.templated.common.tabs

import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.onClick

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
    hxTarget: String,
    vararg tabs: TabData,
    handler: (Tabs.() -> Unit)? = null,
) {
    val component = Tabs(tabs.toList(), hxTarget)
    handler?.invoke(component)
    addComponent(component)
}

class Tabs(
    val tabs: List<TabData>,
    var hxTarget: String? = null,
    var activeTab: String? = null,
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.div("tabs") {
            tabs.forEach {
                button(classes = "btn-secondary tab") {
                    onClick = "document.querySelector('.tabs .active')?.classList.remove('active'); this.classList.add('active');"
                    hxGet("?tab=${it.value}")
                    hxTarget?.let { target -> hxTarget(target) }
                    if (it.value == activeTab) {
                        classes += "active"
                    }
                    + it.label
                }
            }
        }
    }
}