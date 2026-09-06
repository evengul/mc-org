package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.project.DependencyOrigin
import app.mcorg.domain.model.project.NamedProjectId
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectDependency
import kotlinx.html.*
import kotlinx.html.stream.createHTML

private fun dependenciesFieldId(projectId: Int) = "project-dependencies-field-$projectId"

private fun dependenciesBase(worldId: Int, projectId: Int) =
    "/worlds/$worldId/projects/$projectId/dependencies"

/**
 * "Waits on" meta-row field (MCO-302): a chip summarising what this project is declared to wait
 * on, opening the dependency editor in the shared #resource-panel dialog.
 *
 * Sits beside state, location and Produces because it is the same kind of fact — project
 * identity rather than plan content. The roadmap orders the whole world from these rows, so it
 * belongs where a reader can see it without opening the roadmap.
 *
 * Unlike Produces there is no quiet admin affordance for the empty case: an ordering nobody has
 * asserted is the *normal* state — the roadmap derives its order from item-level edges, and a
 * declared row is the exception (see [dependenciesPanel]). Offering "+ Waits on" on every
 * project in a world would advertise hand-curation as the default, which is the opposite of the
 * rule. Admins reach it from the roadmap or by the panel URL.
 */
fun FlowContent.projectDependenciesField(project: Project, dependencies: List<ProjectDependency>) {
    div("project-meta-field project-meta-field--dependencies") {
        id = dependenciesFieldId(project.id)
        dependenciesFieldInner(project.worldId, project.id, dependencies)
    }
}

/** OOB re-render of the meta chip — sidecar on POST/DELETE panel responses. */
fun dependenciesFieldFragment(worldId: Int, projectId: Int, dependencies: List<ProjectDependency>): String =
    createHTML().div("project-meta-field project-meta-field--dependencies") {
        id = dependenciesFieldId(projectId)
        attributes["hx-swap-oob"] = "true"
        dependenciesFieldInner(worldId, projectId, dependencies)
    }

private fun DIV.dependenciesFieldInner(worldId: Int, projectId: Int, dependencies: List<ProjectDependency>) {
    if (dependencies.isEmpty()) return
    val panelUrl = "${dependenciesBase(worldId, projectId)}/panel"
    button(classes = "dependency-chip") {
        type = ButtonType.button
        attributes["data-production-panel-url"] = panelUrl
        attributes["aria-label"] = "View declared dependencies"
        span("dependency-chip__label") { +"⛓ Waits on:" }
        span("dependency-chip__item") { +dependencies.first().dependencyName }
        if (dependencies.size > 1) {
            span("dependency-chip__more") { +"+${dependencies.size - 1} more" }
        }
    }
}

/**
 * The dependency editor panel — swaps into #resource-panel-content, opened by resource-panel.js
 * through the chip's `data-production-panel-url`.
 *
 * **The hint is doing real work.** The roadmap is derived and this is the one place a person
 * overrides it, so the panel has to say what a row here is *for* — an ordering the item-level
 * graph cannot see. Without that the obvious reading is "this is how you declare dependencies",
 * and someone starts hand-curating edges the planner already derives.
 *
 * The picker's options are [available] — `GetAvailableProjectDependenciesStep`'s answer, which
 * has already removed the project itself, other worlds, existing rows and anything that would
 * close a cycle. So the form cannot offer a choice the POST will reject.
 */
fun FlowContent.dependenciesPanel(
    worldId: Int,
    projectId: Int,
    dependencies: List<ProjectDependency>,
    available: List<NamedProjectId>,
) {
    val base = dependenciesBase(worldId, projectId)
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
        h2("resource-panel__item-name") { +"Waits on" }
    }
    p("production-panel__hint") {
        +"Projects this one must follow, for a reason the materials do not show — digging a "
        +"perimeter before the farm inside it. The roadmap already orders projects that supply "
        +"each other; add a row here only when it cannot see the reason."
    }
    div("resource-panel__divider") {}

    if (dependencies.isEmpty()) {
        p("resource-panel__source-empty") { +"No declared dependencies" }
    } else {
        div("production-list") {
            dependencies.forEach { dependency ->
                div("production-row") {
                    span("production-row__name") {
                        +dependency.dependencyName
                        if (dependency.origin == DependencyOrigin.IMPORTED) {
                            // Says who asserted the row, and nothing more — no behaviour is keyed
                            // off it (MCO-302). An imported row is deletable like any other.
                            span("badge badge--neutral dependency-row__origin") { +"Imported" }
                        }
                    }
                    div("production-row__controls") {
                        button(classes = "btn btn--ghost btn--sm production-row__delete") {
                            type = ButtonType.button
                            attributes["aria-label"] = "Stop waiting on ${dependency.dependencyName}"
                            attributes["hx-delete"] = "$base/${dependency.dependencyId}"
                            attributes["hx-target"] = "#resource-panel-content"
                            attributes["hx-swap"] = "innerHTML"
                            +"×"
                        }
                    }
                }
            }
        }
    }

    span("section-label production-panel__add-label") { +"Add a dependency" }
    if (available.isEmpty()) {
        p("resource-panel__source-empty") {
            +"No other project in this world can be added — every one is already a dependency, "
            +"or would create a cycle."
        }
    } else {
        div("production-panel__add") {
            select("form-control") {
                id = "dependency-panel-select"
                // The name is what hx-include actually sends, and its absence is invisible until
                // the POST arrives empty — MCO-463 shipped exactly that bug past green tests.
                // ValidateProjectDependencyInputStep reads this key.
                name = "dependsOnProjectId"
                attributes["aria-label"] = "Project to wait on"
                option {
                    value = ""
                    +"Select a project..."
                }
                available.forEach { candidate ->
                    option {
                        value = candidate.id.toString()
                        +candidate.name
                    }
                }
            }
            button(classes = "btn btn--secondary btn--sm") {
                type = ButtonType.button
                attributes["hx-post"] = base
                attributes["hx-include"] = "#dependency-panel-select"
                attributes["hx-target"] = "#resource-panel-content"
                attributes["hx-swap"] = "innerHTML"
                +"Add"
            }
        }
    }
}

/** Full fragment for GET /dependencies/panel and the POST/DELETE re-renders. */
fun dependenciesPanelFragment(
    worldId: Int,
    projectId: Int,
    dependencies: List<ProjectDependency>,
    available: List<NamedProjectId>,
): String = createHTML().div {
    dependenciesPanel(worldId, projectId, dependencies, available)
}
