package app.mcorg.presentation.templated.common.progress

import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.style

fun <T : Tag> T.progressComponent(
    handler: (Progress.() -> Unit)? = null,
) {
    val component = Progress()
    handler?.invoke(component)
    addComponent(component)
}

enum class ProgressVariant {
    DEFAULT, SUCCESS, WARNING, DANGER, INFO
}

enum class ProgressSize {
    SMALL, MEDIUM, LARGE
}

enum class ProgressDisplay {
    DEFAULT, COMPACT, DETAILED
}

class Progress(
    var id: String? = null,
    var max: Double = 1.0,
    var value: Double = 0.0,
    var variant: ProgressVariant = ProgressVariant.DEFAULT,
    var size: ProgressSize = ProgressSize.MEDIUM,
    var display: ProgressDisplay = ProgressDisplay.DEFAULT,
    var showPercentage: Boolean = true,
    var label: String? = null,
    var classes: MutableSet<String> = mutableSetOf(),
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        val percentage = ((value / max) * 100).coerceIn(0.0, 100.0).toInt()

        container.div {
            this@Progress.id?.let {
                this.id = it
            }
            attributes["data-max"] = max.toString()
            attributes["data-value"] = value.toString()
            attributes["role"] = "progressbar"
            attributes["aria-valuenow"] = value.toString()
            attributes["aria-valuemin"] = "0"
            attributes["aria-valuemax"] = max.toString()

            // Apply base progress class and modifiers
            classes = this@Progress.classes + mutableSetOf("progress").apply {
                // Add variant modifier classes
                when (this@Progress.variant) {
                    ProgressVariant.DEFAULT -> { /* Default variant */ }
                    ProgressVariant.SUCCESS -> add("progress--success")
                    ProgressVariant.WARNING -> add("progress--warning")
                    ProgressVariant.DANGER -> add("progress--danger")
                    ProgressVariant.INFO -> add("progress--info")
                }

                // Add size modifier classes
                when (this@Progress.size) {
                    ProgressSize.SMALL -> add("progress--sm")
                    ProgressSize.MEDIUM -> { /* Default size */ }
                    ProgressSize.LARGE -> add("progress--lg")
                }

                // Add display modifier classes
                when (this@Progress.display) {
                    ProgressDisplay.DEFAULT -> { /* Default display */ }
                    ProgressDisplay.COMPACT -> add("progress--compact")
                    ProgressDisplay.DETAILED -> add("progress--detailed")
                }
            }

            // Progress container
            div {
                classes = setOf("progress__container")

                if (display != ProgressDisplay.COMPACT) {
                    div {
                        classes = setOf("progress__value")
                        + ((if (showPercentage) "$percentage%" else "") + (if (showPercentage && label != null) " - " else "") + (if (label != null) "$label" else ""))
                    }
                }

                // Progress bar
                div {
                    classes = setOf("progress__bar")
                    style = "width: $percentage%;"
                }
            }

            // Detailed display additional info
            if (display == ProgressDisplay.DETAILED) {
                div {
                    classes = setOf("progress__details", "u-flex", "u-flex-between", "u-text-sm")
                    div {
                        classes = setOf("progress__current")
                        + "${value.toInt()} / ${max.toInt()}"
                    }
                    if (showPercentage) {
                        div {
                            classes = setOf("progress__percentage")
                            + "$percentage%"
                        }
                    }
                }
            }
        }
    }
}

// Convenience functions for common progress variants
fun <T : Tag> T.successProgress(
    value: Double,
    max: Double = 1.0,
    label: String? = null,
    size: ProgressSize = ProgressSize.MEDIUM,
    handler: (Progress.() -> Unit)? = null
) {
    progressComponent {
        this.value = value
        this.max = max
        this.variant = ProgressVariant.SUCCESS
        this.size = size
        this.label = label
        handler?.invoke(this)
    }
}

fun <T : Tag> T.warningProgress(
    value: Double,
    max: Double = 1.0,
    label: String? = null,
    size: ProgressSize = ProgressSize.MEDIUM,
    handler: (Progress.() -> Unit)? = null
) {
    progressComponent {
        this.value = value
        this.max = max
        this.variant = ProgressVariant.WARNING
        this.size = size
        this.label = label
        handler?.invoke(this)
    }
}

fun <T : Tag> T.dangerProgress(
    value: Double,
    max: Double = 1.0,
    label: String? = null,
    size: ProgressSize = ProgressSize.MEDIUM,
    handler: (Progress.() -> Unit)? = null
) {
    progressComponent {
        this.value = value
        this.max = max
        this.variant = ProgressVariant.DANGER
        this.size = size
        this.label = label
        handler?.invoke(this)
    }
}