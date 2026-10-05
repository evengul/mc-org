package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapCycle
import app.mcorg.domain.model.world.RoadmapCycleOption
import app.mcorg.domain.model.world.RoadmapCycleOrder
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.SPAN
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.strong

/**
 * The loops in this world, and the question only a person can answer (MCO-460).
 *
 * Two farms that each consume a farm-scale amount of the other's output is not bad data — both
 * edges are derived from real demand and both are true. What it is, is a sequencing decision:
 * which one do you build first, knowing you will hand-gather the other's input until it runs.
 *
 * The page has already broken each loop at its smallest claim so nothing below it shows two
 * projects each blocking the other. That guess is stated rather than hidden — a roadmap that
 * quietly picked an order would be a roadmap you could not trust the rest of.
 *
 * **The roadmap is still derived, never curated** — with this one exception that proves the rule.
 * What an answer records is a *subtraction* — "set this edge aside" — never an ordering someone
 * typed in. Asserting an order is `project_dependencies`, and that is MCO-302's editor.
 */
internal fun FlowContent.cycleSection(roadmap: Roadmap) {
    if (roadmap.cycles.isEmpty() && roadmap.resolvedOrders.isEmpty()) return

    div("roadmap-cycles") {
        id = "roadmap-cycles"
        span("section-label") { +"Circular supply" }

        roadmap.cycles.forEach { cycle -> cyclePrompt(roadmap.worldId, cycle) }
        roadmap.resolvedOrders.forEach { order -> resolvedOrderRow(roadmap.worldId, order) }
    }
}

/** One unanswered loop: what it is, what was assumed, and the alternatives. */
private fun FlowContent.cyclePrompt(worldId: Int, cycle: RoadmapCycle) {
    div("roadmap-cycles__item") {
        p("roadmap-cycles__lead") {
            +cycle.projectNames.joinToString(" and ")
            +" each supply the other. Which comes first?"
        }
        p("roadmap-cycles__assumed") {
            +"Assuming "
            strong { +cycle.breaking.firstProjectName }
            +" for now — its "
            +cycle.breaking.claimText()
            +" is the smaller claim, so it is the easier one to cover by hand."
        }
        div("roadmap-cycles__options") {
            cycle.options.forEach { option ->
                form(classes = "roadmap-cycles__option") {
                    method = FormMethod.post
                    action = "/worlds/$worldId/roadmap/cycle-order"
                    hiddenInput { name = "first"; value = option.firstProjectId.toString() }
                    hiddenInput { name = "waiting"; value = option.waitingProjectId.toString() }
                    button(classes = "btn btn--sm ${if (option == cycle.breaking) "btn--secondary" else "btn--ghost"}") {
                        type = ButtonType.submit
                        +"${option.firstProjectName} first"
                    }
                    span("roadmap-cycles__option-note") {
                        +"sets aside ${option.claimText()} from ${option.waitingProjectName}"
                    }
                }
            }
        }
    }
}

/** A loop somebody has already settled — visible so it can be changed or undone. */
private fun FlowContent.resolvedOrderRow(worldId: Int, order: RoadmapCycleOrder) {
    div("roadmap-cycles__resolved") {
        p("roadmap-cycles__resolved-text") {
            +"You put "
            strong { +order.firstProjectName }
            +" before "
            strong { +order.waitingProjectName }
            +"."
        }
        form(classes = "roadmap-cycles__resolved-undo") {
            method = FormMethod.post
            action = "/worlds/$worldId/roadmap/cycle-order/clear"
            hiddenInput { name = "first"; value = order.firstProjectId.toString() }
            hiddenInput { name = "waiting"; value = order.waitingProjectId.toString() }
            button(classes = "btn btn--sm btn--ghost") {
                type = ButtonType.submit
                +"Undo"
            }
        }
    }
}

/**
 * "2,400 Gunpowder", or "the dependency" when the edge is a declared row with no item.
 *
 * A declared `project_dependencies` edge carries no quantity by design, so this must not
 * invent one — see [RoadmapCycleOption.quantity].
 */
private fun RoadmapCycleOption.claimText(): String =
    if (quantity != null && itemName != null) "%,d %s".format(quantity, itemName)
    else "the dependency"

/**
 * A roadmap section's tracked caps label — "TO BUILD · 6 FARMS", "MANUAL ORDERING · 0" — exposed as
 * a level-2 heading under the page's "Roadmap" h1, so a screen reader can move section to section.
 * The old Table view was one table a reader could navigate; the page that replaced it is nine
 * sections, and without headings they read as one run of text (MCO-529).
 */
internal inline fun FlowContent.sectionLabel(crossinline block: SPAN.() -> Unit) =
    span("rmg-label") {
        attributes["role"] = "heading"
        attributes["aria-level"] = "2"
        block()
    }

/**
 * The roadmap's title and meta line, above its card as on every other page (MCO-505).
 *
 * It carried a Graph / Table switcher until MCO-529 removed the Table view: the graph page had
 * grown its own table (TO BUILD), its own 375px layout (5C) and its own answer for a world with
 * nothing built (5A), so the second view was a second place for every fix to land — and the two
 * had already drifted apart twice (MCO-318, MCO-505).
 */
internal fun FlowContent.roadmapTitle(meta: String) {
    div("roadmap-title") {
        div {
            h1("roadmap-title__name") { +"Roadmap" }
            div("roadmap-title__meta") { +meta }
        }
    }
}
