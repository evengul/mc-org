package app.mcorg.presentation.templated.dsl

import kotlinx.html.*

fun FlowContent.resourceRow(
    id: Int,
    worldId: Int,
    projectId: Int,
    itemName: String,
    current: Int,
    required: Int,
    source: String? = null
) {
    val percent = if (required > 0) (current.coerceAtMost(required) * 100 / required) else 0
    val complete = required > 0 && current >= required

    div("resource-row${if (complete) " resource-row--complete" else ""}") {
        this.id = "resource-row-$id"
        attributes["data-item-name"] = itemName
        attributes["data-progress-pct"] = percent.toString()
        attributes["data-required"] = required.toString()
        attributes["data-world-id"] = worldId.toString()
        attributes["data-project-id"] = projectId.toString()

        div("resource-row__desktop") {
            div("resource-row__name${if (complete) " resource-row__name--complete" else ""}") {
                +itemName
            }

            div("resource-row__progress") {
                div("progress") {
                    div("progress__fill${if (complete) " progress__fill--complete" else ""}") {
                        attributes["style"] = "width: ${percent}%"
                        attributes["role"] = "progressbar"
                        attributes["aria-valuenow"] = current.toString()
                        attributes["aria-valuemin"] = "0"
                        attributes["aria-valuemax"] = required.toString()
                    }
                }
            }

            span("resource-row__count${if (complete) " resource-row__count--complete" else ""}") {
                this.id = "count-$id"
                attributes["data-resource-id"] = id.toString()
                attributes["data-current"] = current.toString()
                attributes["data-required"] = required.toString()
                +"$current / $required"
            }

            if (source != null) {
                span("resource-row__source") {
                    +"Source: $source"
                }
            }

            div("resource-row__counters") {
                intArrayOf(-1728, -64, -1, 1, 64, 1728).forEach { amount ->
                    button(classes = "btn btn--ghost btn--sm resource-row__counter-btn") {
                        attributes["hx-patch"] =
                            "/worlds/$worldId/projects/$projectId/resources/gathering/$id/edit-done"
                        attributes["hx-vals"] = """{"amount": $amount}"""
                        attributes["hx-target"] = "#resource-row-$id"
                        attributes["hx-swap"] = "outerHTML"
                        +if (amount > 0) "+$amount" else "$amount"
                    }
                }
            }
        }
    }
}
