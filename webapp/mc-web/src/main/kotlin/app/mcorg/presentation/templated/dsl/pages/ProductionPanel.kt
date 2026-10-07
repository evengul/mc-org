package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.model.project.ProjectProductionMode
import app.mcorg.presentation.templated.dsl.BadgeVariant
import app.mcorg.presentation.templated.dsl.badge
import kotlinx.html.*
import kotlinx.html.stream.createHTML

private fun productionsFieldId(projectId: Int) = "project-productions-field-$projectId"

private fun productionsBase(worldId: Int, projectId: Int) = "/worlds/$worldId/projects/$projectId/productions"

/**
 * What the production panel and chip draw (MCO-413).
 *
 * @param productions what the project supplies now — its mode-less list, or its running mode's.
 * @param modes the runtime modes it can be switched between; empty for most projects.
 * @param modeProductions every mode's rates, running or not, so each mode can say what it makes.
 * @param switched set only on the response to a switch, for the note that says what changed.
 */
data class ProductionsView(
    val productions: List<ProjectProduction>,
    val modes: List<ProjectProductionMode> = emptyList(),
    val modeProductions: List<ProjectProduction> = emptyList(),
    val switched: ModeSwitch? = null,
) {
    val runningMode: ProjectProductionMode? get() = modes.firstOrNull { it.active }

    fun productionsOf(mode: ProjectProductionMode): List<ProjectProduction> =
        modeProductions.filter { it.modeId == mode.id }

    /**
     * A design whose modes are all "X Mode" reads better as a list of X — the tree farm's seven rows
     * are Oak, Birch, Spruce, … The full name stays wherever a mode is named on its own.
     */
    fun shortName(mode: ProjectProductionMode): String =
        if (modes.all { it.name.endsWith(MODE_SUFFIX) }) mode.name.removeSuffix(MODE_SUFFIX) else mode.name

    private companion object {
        const val MODE_SUFFIX = " Mode"
    }
}

/** The mode a switch moved away from, and whether the farm was supplying the world when it did. */
data class ModeSwitch(val from: ProjectProductionMode, val farmIsDone: Boolean)

/**
 * "Produces" meta-row field (MCO-297): a chip summarising the project's produced items,
 * opening the productions editor in the shared #resource-panel dialog. Production is
 * project identity — a DONE project with productions supplies the whole world (MCO-296) —
 * so it sits beside state and location. With no productions it is a quiet "+ Produces".
 *
 * The chip keeps naming items, because "what does this supply" is why it exists; a farm with
 * modes adds the running mode as a tag (MCO-413, frame 1a).
 *
 * Every world member edits here: inside a project members do what admins do, and admin is kept
 * for the world-level things (deleting the world, switching its version).
 */
fun FlowContent.projectProductionsField(project: Project, view: ProductionsView) {
    div("project-meta-field project-meta-field--productions") {
        id = productionsFieldId(project.id)
        productionsFieldInner(project.worldId, project.id, view)
    }
}

/** OOB re-render of the meta chip — sidecar on POST/DELETE panel responses. */
fun productionsFieldFragment(worldId: Int, projectId: Int, view: ProductionsView): String =
    createHTML().div("project-meta-field project-meta-field--productions") {
        id = productionsFieldId(projectId)
        attributes["hx-swap-oob"] = "true"
        productionsFieldInner(worldId, projectId, view)
    }

private fun DIV.productionsFieldInner(worldId: Int, projectId: Int, view: ProductionsView) {
    val panelUrl = "${productionsBase(worldId, projectId)}/panel"
    val productions = view.productions
    if (productions.isNotEmpty()) {
        button(classes = "production-chip") {
            type = ButtonType.button
            attributes["data-production-panel-url"] = panelUrl
            span("production-chip__label") { +"⚙ Produces:" }
            val first = productions.first()
            span("production-chip__item") {
                +first.name
                if (first.ratePerHour > 0) +" ${"%,d".format(first.ratePerHour)}/hr"
            }
            if (productions.size > 1) {
                span("production-chip__more") { +"+${productions.size - 1} more" }
            }
            view.runningMode?.let { badge(it.name, BadgeVariant.NEUTRAL) }
        }
    } else {
        button(classes = "btn btn--ghost btn--sm") {
            type = ButtonType.button
            attributes["data-production-panel-url"] = panelUrl
            +"+ Produces"
        }
    }
}

/**
 * Productions editor panel — swaps into #resource-panel-content (opened by
 * resource-panel.js via the chip's data-production-panel-url). Rate inputs upsert on
 * change; the add search reuses /items/search with a panel-scoped results container
 * (same capture-phase selection pattern as the variant search).
 *
 * A farm with runtime modes (MCO-413) gets frame 1a's ledger above the rates: every mode is a row
 * saying what it makes, the running one marked by a filled dot and the word RUNNING (never colour
 * alone), the others one tap from running.
 */
fun FlowContent.productionsPanel(worldId: Int, projectId: Int, view: ProductionsView) {
    val base = productionsBase(worldId, projectId)
    val running = view.runningMode
    div("resource-panel__header") {
        button(classes = "resource-panel__close-btn") {
            type = ButtonType.button
            attributes["data-resource-panel-close"] = "true"
            attributes["aria-label"] = "Back"
            +"←"
        }
        button(classes = "resource-panel__close-btn resource-panel__close-btn--x") {
            type = ButtonType.button
            attributes["data-resource-panel-close"] = "true"
            attributes["aria-label"] = "Close"
            +"×"
        }
    }
    div("resource-panel__title") {
        h2("resource-panel__item-name") { +"Produces" }
    }
    p("production-panel__hint") {
        +"Items this project outputs. Once the project is Done, they supply every other project's gathering plan."
    }
    view.switched?.let { switchNote(base, view, it) }
    if (view.modes.isNotEmpty()) modeLedger(base, view)
    div("resource-panel__divider") {}

    if (running != null) span("section-label") { +"Makes in ${running.name}" }
    if (view.productions.isEmpty()) {
        p("resource-panel__source-empty") { +"No produced items declared" }
    } else {
        div("production-list") {
            view.productions.forEach { production ->
                div("production-row") {
                    span("production-row__name") { +production.name }
                    div("production-row__controls") {
                        input(type = InputType.number, classes = "form-control production-row__rate") {
                            value = production.ratePerHour.toString()
                            min = "0"
                            attributes["aria-label"] = "${production.name} rate per hour"
                            attributes["hx-post"] = base
                            attributes["hx-vals"] = """js:{itemId:"${production.itemId}",ratePerHour:event.target.value}"""
                            attributes["hx-target"] = "#resource-panel-content"
                            attributes["hx-swap"] = "innerHTML"
                            attributes["hx-trigger"] = "change"
                        }
                        span("production-row__unit") { +"/hr" }
                        button(classes = "btn btn--ghost btn--sm production-row__delete") {
                            type = ButtonType.button
                            attributes["aria-label"] = "Remove ${production.name}"
                            attributes["hx-delete"] = "$base/${production.id}"
                            attributes["hx-target"] = "#resource-panel-content"
                            attributes["hx-swap"] = "innerHTML"
                            +"×"
                        }
                    }
                }
            }
        }
    }

    span("section-label production-panel__add-label") {
        +(if (running != null) "Add to ${running.name}" else "Add produced item")
    }
    div("production-panel__add") {
        input(type = InputType.number, classes = "form-control production-row__rate") {
            id = "production-panel-rate"
            placeholder = "rate/hr"
            min = "0"
            attributes["aria-label"] = "Rate per hour for the item to add"
        }
        div("item-search-field") {
            input(type = InputType.text, classes = "form-control") {
                id = "production-panel-item-input"
                placeholder = "Search items by name..."
                autoComplete = "off"
                attributes["hx-get"] = "/items/search"
                attributes["hx-trigger"] = "input changed delay:300ms"
                attributes["hx-target"] = "#production-panel-item-results"
                attributes["hx-swap"] = "innerHTML"
                attributes["hx-vals"] = "js:{q: this.value}"
            }
            div("item-search-results") {
                id = "production-panel-item-results"
                attributes["data-production-add-url"] = base
            }
        }
    }
}

/** Frame 1a: every mode a row, saying what it makes. Rendered only for a project with modes. */
private fun FlowContent.modeLedger(base: String, view: ProductionsView) {
    div("mode-ledger") {
        div("mode-ledger__head") {
            span("section-label") { +"Modes · ${view.modes.size}" }
            span("subtle") { +"Anyone in the world can switch" }
        }
        div("mode-ledger__rows") {
            view.modes.forEach { mode ->
                val makes = makesLine(view.productionsOf(mode).map { it.name to it.ratePerHour })
                if (mode.active) {
                    div("mode-ledger__row mode-ledger__row--running") {
                        span("mode-ledger__dot mode-ledger__dot--running") {}
                        span("mode-ledger__text") {
                            span("mode-ledger__name") { +view.shortName(mode) }
                            span("mode-ledger__makes") { +makes }
                        }
                        span("mode-ledger__state") { +"RUNNING" }
                    }
                } else {
                    // No aria-label: it would replace the row's own text, and what each mode makes
                    // is the point of the ledger. The row reads "Oak, Oak Log · 48,000/hr, Switch".
                    button(classes = "mode-ledger__row") {
                        type = ButtonType.button
                        switchTo(base, mode)
                        span("mode-ledger__dot") {}
                        span("mode-ledger__text") {
                            span("mode-ledger__name") { +view.shortName(mode) }
                            span("mode-ledger__makes") { +makes }
                        }
                        span("mode-ledger__action") { +"Switch" }
                    }
                }
            }
        }
    }
}

/**
 * The quiet note on the response to a switch, when the farm is Done and so the switch changed other
 * projects' plans. Not a confirm step: a switch is one tap to undo, and the note carries that tap.
 *
 * It says what stopped coming *from this farm*, not that it is "back on hand-gathering" — another
 * Done farm may well make it, and this note does not look.
 */
private fun FlowContent.switchNote(base: String, view: ProductionsView, switched: ModeSwitch) {
    val running = view.runningMode ?: return
    if (!switched.farmIsDone || switched.from.id == running.id) return
    val now = view.productions.map { it.name }.distinct()
    val nowIds = view.productions.map { it.itemId }.toSet()
    val lost = view.productionsOf(switched.from).filter { it.itemId !in nowIds }.map { it.name }.distinct()

    div("callout callout--info production-panel__note") {
        // The switch replaced the button that had focus; this is how a screen reader hears it.
        attributes["role"] = "status"
        span("callout__icon") {
            attributes["aria-hidden"] = "true"
            +"i"
        }
        div("callout__body") {
            p {
                +"Now running ${running.name}."
                if (now.isNotEmpty()) +" Other projects' gathering plans count ${joinNames(now)} from this farm"
                +(if (lost.isEmpty()) "." else "; ${joinNames(lost)} no longer ${if (lost.size == 1) "comes" else "come"} from it.")
            }
            button(classes = "production-panel__undo") {
                type = ButtonType.button
                switchTo(base, switched.from)
                +"Switch back to ${switched.from.name}"
            }
        }
    }
}

private fun HTMLTag.switchTo(base: String, mode: ProjectProductionMode) {
    attributes["hx-post"] = "$base/active-mode"
    attributes["hx-vals"] = """{"modeId":"${mode.id}"}"""
    attributes["hx-target"] = "#resource-panel-content"
    attributes["hx-swap"] = "innerHTML"
}

/**
 * What one mode makes, as (item name, rate per hour) pairs: "Oak Log · 48,000/hr" for one item,
 * "Bone 700 · Blaze Rod 500 /hr" for several. Shared by the panel's ledger and the import review.
 */
internal fun makesLine(items: List<Pair<String, Int>>): String = when (items.size) {
    0 -> "Nothing recorded"
    1 -> items.single().let { (name, rate) -> "$name · ${rateText(rate)}" }
    else -> items.joinToString(" · ") { (name, rate) ->
        "$name ${if (rate > 0) "%,d".format(rate) else "?"}"
    } + " /hr"
}

private fun rateText(rate: Int) = if (rate > 0) "${"%,d".format(rate)}/hr" else "rate unknown"

private fun joinNames(names: List<String>): String =
    if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()

/** Full fragment for GET /productions/panel and the POST/DELETE re-renders. */
fun productionsPanelFragment(worldId: Int, projectId: Int, view: ProductionsView): String =
    createHTML().div {
        productionsPanel(worldId, projectId, view)
    }
