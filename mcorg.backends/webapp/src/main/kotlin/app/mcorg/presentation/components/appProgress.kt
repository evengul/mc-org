package app.mcorg.presentation.components

import kotlinx.html.*
import kotlin.math.floor
import kotlin.math.roundToInt

@HtmlTagMarker
fun FlowOrPhrasingContent.appProgress(progressClasses: Set<String> = emptySet(),
                                      max: Double = 1.0,
                                      value: Double = 0.0,
                                      isItemAmount: Boolean = false
) {
    val percentage = ((value / max) * 100).roundToInt()

    span {
        attributes["data-max"] = max.toString()
        attributes["data-value"] = value.toString()
        classes = setOf("app-progress") + progressClasses
        title = if (isItemAmount) value.roundToInt().toString() + " of " + max.roundToInt().toString() else ""
        p {
            classes = setOf("app-progress-value")
            if (isItemAmount) {
                + (getItemProgress(value) + " of " + getItemProgress(max) + " = " + percentage + "%")
            } else {
                + "$percentage%"
            }
        }
        span {
            style = "right: ${100 - percentage}%"
            classes = setOf("app-progress-value-bar")
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