package app.mcorg.presentation.templated.common.dangerzone

import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.p

fun <T : Tag> T.dangerZone(
    title: String = "Danger Zone",
    description: String? = null,
    content: DIV.() -> Unit = { },
) {
    val dangerZone = DangerZone(title, description, content)
    addComponent(dangerZone)
}

class DangerZone(
    val title: String = "Danger Zone",
    val description: String? = null,
    val content: DIV.() -> Unit = { },
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.div {
            classes += "danger-zone"
            p("danger-zone__title") {
                + title
            }
            description?.let {
                p("danger-zone__description subtle") {
                    + it
                }
            }
            div {
                classes = setOf("danger-zone__content")
                content.invoke(this)
            }
        }
    }
}