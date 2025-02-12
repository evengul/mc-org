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