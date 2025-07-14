package app.mcorg.presentation.templated.common.tabs

import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.onClick

fun <T : Tag> T.tabsComponent(
    vararg tabs: String,
    handler: (Tabs.() -> Unit)? = null,
) {
    val component = Tabs(tabs.toList())
    handler?.invoke(component)
    addComponent(component)
}

class Tabs(
    val tabs: List<String>,
    var activeTab: String? = null,
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.div("tabs") {
            tabs.forEach {
                button(classes = "btn-secondary tab") {
                    onClick = "document.querySelector('.tabs .active')?.classList.remove('active'); this.classList.add('active');"
                    if (it == activeTab) {
                        classes += "active"
                    }
                    + it
                }
            }
        }
    }
}