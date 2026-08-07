package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectProduction
import kotlinx.html.*
import kotlinx.html.stream.createHTML

private fun productionsFieldId(projectId: Int) = "project-productions-field-$projectId"

private fun productionsBase(worldId: Int, projectId: Int) = "/worlds/$worldId/projects/$projectId/productions"

/**
 * "Produces" meta-row field (MCO-297): a chip summarising the project's produced items,
 * opening the productions editor in the shared #resource-panel dialog. Production is
 * project identity — a DONE project with productions supplies the whole world (MCO-296) —
 * so it sits beside state and location. Admins with no productions get a quiet
 * "+ Produces" affordance; members with no productions see nothing.
 */
fun FlowContent.projectProductionsField(project: Project, productions: List<ProjectProduction>, isAdmin: Boolean) {
    div("project-meta-field project-meta-field--productions") {
        id = productionsFieldId(project.id)
        productionsFieldInner(project.worldId, project.id, productions, isAdmin)
    }
}

/** OOB re-render of the meta chip — sidecar on POST/DELETE panel responses. */
fun productionsFieldFragment(worldId: Int, projectId: Int, productions: List<ProjectProduction>, isAdmin: Boolean): String =
    createHTML().div("project-meta-field project-meta-field--productions") {
        id = productionsFieldId(projectId)
        attributes["hx-swap-oob"] = "true"
        productionsFieldInner(worldId, projectId, productions, isAdmin)
    }

private fun DIV.productionsFieldInner(worldId: Int, projectId: Int, productions: List<ProjectProduction>, isAdmin: Boolean) {
    val panelUrl = "${productionsBase(worldId, projectId)}/panel"
    when {
        productions.isNotEmpty() -> {
            button(classes = "production-chip") {
                type = ButtonType.button
                attributes["data-production-panel-url"] = panelUrl
                attributes["aria-label"] = "View produced items"
                span("production-chip__label") { +"⚙ Produces:" }
                val first = productions.first()
                span("production-chip__item") {
                    +first.name
                    if (first.ratePerHour > 0) +" ${first.ratePerHour}/hr"
                }
                if (productions.size > 1) {
                    span("production-chip__more") { +"+${productions.size - 1} more" }
                }
            }
        }
        isAdmin -> {
            button(classes = "btn btn--ghost btn--sm") {
                type = ButtonType.button
                attributes["data-production-panel-url"] = panelUrl
                +"+ Produces"
            }
        }
    }
}

/**
 * Productions editor panel — swaps into #resource-panel-content (opened by
 * resource-panel.js via the chip's data-production-panel-url). Rate inputs upsert on
 * change; the add search reuses /items/search with a panel-scoped results container
 * (same capture-phase selection pattern as the variant search).
 */
fun FlowContent.productionsPanel(worldId: Int, projectId: Int, productions: List<ProjectProduction>, isAdmin: Boolean) {
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
        +"Items this project outputs. Once the project is Done, they supply every other project's gathering plan."
    }
    div("resource-panel__divider") {}

    if (productions.isEmpty()) {
        p("resource-panel__source-empty") { +"No produced items declared" }
    } else {
        div("production-list") {
            productions.forEach { production ->
                div("production-row") {
                    span("production-row__name") { +production.name }
                    if (isAdmin) {
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
                    } else {
                        span("production-row__unit") {
                            +(if (production.ratePerHour > 0) "${production.ratePerHour}/hr" else "rate unknown")
                        }
                    }
                }
            }
        }
    }

    if (isAdmin) {
        span("section-label production-panel__add-label") { +"Add produced item" }
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
}

/** Full fragment for GET /productions/panel and the POST/DELETE re-renders. */
fun productionsPanelFragment(worldId: Int, projectId: Int, productions: List<ProjectProduction>, isAdmin: Boolean): String =
    createHTML().div {
        productionsPanel(worldId, projectId, productions, isAdmin)
    }
