package app.mcorg.presentation.components

import kotlinx.html.*
import kotlin.math.floor
import kotlin.math.roundToInt

@HtmlTagMarker
fun FlowContent.appProgress(progressClasses: Set<String> = emptySet(),
                                      max: Double = 1.0,
                                      value: Double = 0.0,
                                      isItemAmount: Boolean = false
) {
    val percentage = ((value / max) * 100).roundToInt()

    val animationDuration = 1.5 * (percentage / 100.0)

    div {
        attributes["data-max"] = max.toString()
        attributes["data-value"] = value.toString()
        classes = setOf("app-progress-container") + progressClasses
        title = if (isItemAmount) value.roundToInt().toString() + " of " + max.roundToInt().toString() else ""
        div {
            classes = setOf("app-progress-bar")
            style = "width: $percentage%; --progress-value: $percentage%; animation: progress-animation ${animationDuration}s ease-out;"
            div {
                classes = setOf("app-progress-value")
                + "$percentage%"
            }
        }
    }
}

fun getItemProgress(amount: Double): String {
    val shulkers = floor(amount / 1728.0).roundToInt()
    val stacks = floor((amount - shulkers * 1728.0) / 64.0).roundToInt()
    val items = floor(amount - shulkers * 1728 - stacks * 64.0).roundToInt()

    var content = ""
    if (shulkers > 0) {
        content += "$shulkers shulker boxes"
        if (stacks > 0 || amount > 0) {
            content += ", "
        }
    }
    if (stacks > 0) {
        content += "$stacks stacks"
        if (amount > 0) {
            content += ", "
        }
    }
    if (items > 0) {
        content += "$items items"
    }
    return content.takeIf { it.isNotEmpty() } ?: "0"
}