package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent
import kotlinx.html.div

fun FlowContent.progressBar(current: Int, total: Int, large: Boolean = false) {
    val percent = if (total > 0) (current.coerceAtMost(total) * 100) / total else 0
    val complete = current >= total && total > 0
    val sizeClass = if (large) " progress--lg" else ""

    div("progress$sizeClass") {
        div("progress__fill${if (complete) " progress__fill--complete" else ""}") {
            attributes["style"] = "width: ${percent}%"
            attributes["role"] = "progressbar"
            attributes["aria-valuenow"] = current.toString()
            attributes["aria-valuemin"] = "0"
            attributes["aria-valuemax"] = total.toString()
        }
    }
}
