package app.mcorg.presentation.templated.common.progress

import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.style

fun <T : Tag> T.progressComponent(
    handler: (Progress.() -> Unit)? = null,
) {
    val component = Progress()
    handler?.invoke(component)
    addComponent(component)
}

class Progress(
    var max: Double = 1.0,
    var value: Double = 0.0,
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        val percentage = ((value / max) * 100).toInt()
        container.div {
            attributes["data-max"] = max.toString()
            attributes["data-value"] = value.toString()
            classes = setOf("app-progress-container")
            div {
                classes = setOf("app-progress-bar")
                style = "width: $percentage%; --progress-value: $percentage%;"
                div {
                    classes = setOf("app-progress-value")
                    + "$percentage%"
                }
            }
        }
    }
}