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
 * @param productions every row the project has — its one mode-less list, or each mode's rates.
 * @param modes the ways the farm can be run; empty for most projects. Every one of them supplies
 *   once the project is Done (MCO-588).
 * @param lastModeId the mode the write this view answers went to, so the add form's picker stays
 *   on it — a picker reset to the first mode would file the next item there without a word.
 */
data class ProductionsView(
    val productions: List<ProjectProduction>,
    val modes: List<ProjectProductionMode> = emptyList(),
    val lastModeId: Int? = null,
) {
    fun productionsOf(mode: ProjectProductionMode): List<ProjectProduction> =
        productions.filter { it.modeId == mode.id }

    /**
     * What the project supplies, one entry per item, each at the best rate any mode makes it at —
     * the tree farm's seven modes all make sticks, and the chip names sticks once.
     */
    val suppliedItems: List<ProjectProduction>
        get() = productions.groupBy { it.itemId }.values.map { rows -> rows.maxBy { it.ratePerHour } }
}

/**
 * "Produces" meta-row field (MCO-297): a chip summarising the project's produced items,
 * opening the productions editor in the shared #resource-panel dialog. Production is
 * project identity — a DONE project with productions supplies the whole world (MCO-296) —
 * so it sits beside state and location. With no productions it is a quiet "+ Produces".
 *
 * The chip names items, because "what does this supply" is why it exists; a farm with modes adds
 * how many it has as a tag (MCO-413).
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
    val productions = view.suppliedItems
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
            if (view.modes.isNotEmpty()) badge("${view.modes.size} modes", BadgeVariant.NEUTRAL)
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
 * A farm with runtime modes (MCO-413) lists each mode with its own rates, every one editable, and
 * the add form asks which mode the item belongs to. No mode is marked as running: all of them
 * supply once the farm is Done (MCO-588).
 */
fun FlowContent.productionsPanel(worldId: Int, projectId: Int, view: ProductionsView) {
    val base = productionsBase(worldId, projectId)
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
        if (view.modes.isEmpty()) {
            +"Items this project outputs. Once the project is Done, they supply every other project's gathering plan."
        } else {
            +"Items this farm outputs in each of its modes. Once the project is Done, everything any mode makes supplies every other project's gathering plan."
        }
    }
    div("resource-panel__divider") {}

    if (view.modes.isEmpty()) {
        productionList(base, view.productions)
    } else {
        div("production-panel__modes") {
            span("section-label") { +"Modes · ${view.modes.size}" }
            view.modes.forEach { mode ->
                div("production-panel__mode") {
                    span("production-panel__mode-name") { +mode.name }
                    productionList(base, view.productionsOf(mode), mode)
                }
            }
        }
    }

    span("section-label production-panel__add-label") { +"Add produced item" }
    div("production-panel__add") {
        if (view.modes.isNotEmpty()) {
            select("form-control") {
                id = "production-panel-mode"
                attributes["aria-label"] = "Mode the item to add belongs to"
                view.modes.forEach { mode ->
                    option {
                        value = mode.id.toString()
                        selected = mode.id == view.lastModeId
                        +mode.name
                    }
                }
            }
        }
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

/** One list of produced items with editable rates: the project's own, or one mode's. */
private fun FlowContent.productionList(base: String, productions: List<ProjectProduction>, mode: ProjectProductionMode? = null) {
    if (productions.isEmpty()) {
        p("resource-panel__source-empty") { +"No produced items declared" }
        return
    }
    // A rate edit names its mode, so it lands in that mode's row and not in a list beside it.
    val modeVal = mode?.let { ",modeId:\"${it.id}\"" }.orEmpty()
    div("production-list") {
        productions.forEach { production ->
            div("production-row") {
                span("production-row__name") { +production.name }
                div("production-row__controls") {
                    input(type = InputType.number, classes = "form-control production-row__rate") {
                        value = production.ratePerHour.toString()
                        min = "0"
                        attributes["aria-label"] = "${production.name} rate per hour" + (mode?.let { " in ${it.name}" } ?: "")
                        attributes["hx-post"] = base
                        attributes["hx-vals"] = """js:{itemId:"${production.itemId}",ratePerHour:event.target.value$modeVal}"""
                        attributes["hx-target"] = "#resource-panel-content"
                        attributes["hx-swap"] = "innerHTML"
                        attributes["hx-trigger"] = "change"
                    }
                    span("production-row__unit") { +"/hr" }
                    button(classes = "btn btn--ghost btn--sm production-row__delete") {
                        type = ButtonType.button
                        attributes["aria-label"] = "Remove ${production.name}" + (mode?.let { " from ${it.name}" } ?: "")
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

/**
 * What one mode makes, as (item name, rate per hour) pairs: "Oak Log · 48,000/hr" for one item,
 * "Bone 700 · Blaze Rod 500 /hr" for several. Used by the import review's list of modes.
 */
internal fun makesLine(items: List<Pair<String, Int>>): String = when (items.size) {
    0 -> "Nothing recorded"
    1 -> items.single().let { (name, rate) -> "$name · ${rateText(rate)}" }
    else -> items.joinToString(" · ") { (name, rate) ->
        "$name ${if (rate > 0) "%,d".format(rate) else "?"}"
    } + " /hr"
}

private fun rateText(rate: Int) = if (rate > 0) "${"%,d".format(rate)}/hr" else "rate unknown"

/** Full fragment for GET /productions/panel and the POST/DELETE re-renders. */
fun productionsPanelFragment(worldId: Int, projectId: Int, view: ProductionsView): String =
    createHTML().div {
        productionsPanel(worldId, projectId, view)
    }
