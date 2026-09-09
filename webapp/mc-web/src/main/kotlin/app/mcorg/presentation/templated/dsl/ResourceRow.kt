package app.mcorg.presentation.templated.dsl

import app.mcorg.pipeline.resources.MeasuredStock
import kotlinx.html.*
import java.time.Duration
import java.time.Instant

fun FlowContent.resourceRow(
    id: Int,
    worldId: Int,
    projectId: Int,
    itemName: String,
    current: Int,
    required: Int,
    source: String? = null,
    measured: MeasuredStock? = null,
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

            driftChip(worldId, projectId, id, itemName, current, measured)

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

/**
 * What the chests say, when it differs from what someone typed (MCO-539).
 *
 * Rendered **only** on disagreement, and only where there are tagged containers at all. A project
 * mid-tagging would otherwise grow a column of `-everything` chips saying nothing except that the
 * work is not finished, which is both noise and a lie about the data.
 *
 * The delta is signed because the direction is the whole message, and negative is the *expected*
 * state rather than an error: it means storage exists that has not been tagged yet. Positive means
 * there is more than anyone thought.
 *
 * The sign is never the only signal — the number and the word both carry it — because colour alone
 * fails a colour-blind reader. See docs-product.
 */
private fun FlowContent.driftChip(
    worldId: Int,
    projectId: Int,
    resourceId: Int,
    itemName: String,
    current: Int,
    measured: MeasuredStock?,
) {
    if (measured == null || measured.containerCount == 0) return
    val delta = measured.measured - current
    if (delta == 0L) return

    span("resource-row__drift ${if (delta < 0) "resource-row__drift--under" else "resource-row__drift--over"}") {
        attributes["title"] = driftTitle(measured)
        +"${measured.measured} in ${measured.containerCount} ${if (measured.containerCount == 1) "chest" else "chests"}"
        span("resource-row__drift-delta") { +(if (delta > 0) "+$delta" else "$delta") }
    }
    button(classes = "btn btn--ghost btn--sm resource-row__adopt") {
        attributes["hx-post"] =
            "/worlds/$worldId/projects/$projectId/resources/gathering/$resourceId/adopt"
        attributes["hx-target"] = "#resource-row-$resourceId"
        attributes["hx-swap"] = "outerHTML"
        attributes["aria-label"] = "Set $itemName to ${measured.measured}, what the chests hold"
        +"Adopt"
    }
}

/**
 * How much to trust the number, which is the question a drift chip provokes.
 *
 * The age is of the *oldest* contributing reading: one chest nobody has walked past in a week is
 * what makes a total stale, and an average would hide exactly that.
 */
private fun driftTitle(measured: MeasuredStock): String {
    val seen = measured.oldestSeenAt ?: return "Measured in ${measured.containerCount} tagged container(s)."
    val age = Duration.between(seen, Instant.now())
    val ago = when {
        age.toMinutes() < 1 -> "just now"
        age.toHours() < 1 -> "${age.toMinutes()}m ago"
        age.toDays() < 1 -> "${age.toHours()}h ago"
        else -> "${age.toDays()}d ago"
    }
    return "Measured in ${measured.containerCount} tagged container(s). " +
        "Oldest reading $ago — a container nobody has been near is not re-read."
}
