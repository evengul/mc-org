package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.project.ProjectListItem
import app.mcorg.domain.model.project.ProjectPlanListItem
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTargetError
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.emptyStateCards
import app.mcorg.presentation.templated.dsl.modalForm
import app.mcorg.presentation.templated.dsl.planExecuteToggle
import app.mcorg.presentation.templated.dsl.planProjectCard
import app.mcorg.presentation.templated.dsl.planProjectCardList
import app.mcorg.presentation.templated.dsl.projectCard
import app.mcorg.presentation.templated.dsl.projectCardList
import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.main
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.textArea

fun projectListPage(
    user: TokenProfile,
    world: World,
    projects: List<ProjectListItem>,
    view: String = "execute"
): String = pageShell(
    pageTitle = "MC-ORG — ${world.name}",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/modal.css",
        "/static/styles/components/toggle.css",
        "/static/styles/components/project-card.css",
        "/static/styles/pages/project-list.css",
    )
) {
    appHeader(
        worldName = world.name,
        worldId = world.id,
        user = user,
        breadcrumbBlock = {
            link("Worlds", "/worlds").current(world.name)
        }
    )
    main {
        container {
            div {
                id = "projects-content"
                projectsContent(user, world, projects, view)
            }
        }
    }
}

fun projectListPageWithPlanView(
    user: TokenProfile,
    world: World,
    projects: List<ProjectPlanListItem>
): String = pageShell(
    pageTitle = "MC-ORG — ${world.name}",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/modal.css",
        "/static/styles/components/toggle.css",
        "/static/styles/components/project-card.css",
        "/static/styles/pages/project-list.css",
    )
) {
    appHeader(
        worldName = world.name,
        worldId = world.id,
        user = user,
        breadcrumbBlock = {
            link("Worlds", "/worlds").current(world.name)
        }
    )
    main {
        container {
            div {
                id = "projects-content"
                projectsContentPlan(user, world, projects)
            }
        }
    }
}

fun kotlinx.html.FlowContent.projectsContent(
    user: TokenProfile,
    world: World,
    projects: List<ProjectListItem>,
    view: String = "execute"
) {
    div {
        id = "projects-view"
        projectsViewContent(world, projects, view)
    }
    createProjectModal(world.id, view)
}

fun kotlinx.html.FlowContent.projectsContentPlan(
    user: TokenProfile,
    world: World,
    projects: List<ProjectPlanListItem>
) {
    div {
        id = "projects-view"
        projectsViewContentPlan(world, projects)
    }
    createProjectModal(world.id, "plan")
}

fun kotlinx.html.FlowContent.projectsViewContent(
    world: World,
    projects: List<ProjectListItem>,
    view: String = "execute"
) {
    div {
        id = "projects-toolbar-slot"
        if (projects.isNotEmpty()) { projectsToolbar(world.id, view) }
    }

    if (projects.isEmpty()) {
        projectsEmptyState(world.id)
    }

    projectCardList(world.id, projects)
}

fun kotlinx.html.FlowContent.projectsViewContentPlan(
    world: World,
    projects: List<ProjectPlanListItem>
) {
    div {
        id = "projects-toolbar-slot"
        if (projects.isNotEmpty()) { projectsToolbar(world.id, "plan") }
    }

    if (projects.isEmpty()) {
        projectsEmptyState(world.id)
    }

    planProjectCardList(world.id, projects)
}

fun projectsViewFragment(
    world: World,
    projects: List<ProjectListItem>,
    view: String = "execute"
): String {
    return kotlinx.html.stream.createHTML().div {
        id = "projects-view"
        projectsViewContent(world, projects, view)
    }
}

fun projectsViewFragmentPlan(
    world: World,
    projects: List<ProjectPlanListItem>
): String {
    return kotlinx.html.stream.createHTML().div {
        id = "projects-view"
        projectsViewContentPlan(world, projects)
    }
}

fun kotlinx.html.FlowContent.projectsEmptyState(worldId: Int) {
    emptyStateCards(id = "projects-empty-state") {
        div("empty-state-card") {
            h2("empty-state-card__heading") { +"Plan your own project" }
            p("empty-state-card__body") { +"Create a new project and start tracking your builds and resources." }
            div("empty-state-card__actions") {
                button {
                    classes = setOf("btn", "btn--primary")
                    attributes["onclick"] =
                        "document.getElementById('first-project-flag').value='true'; document.getElementById('create-project-modal')?.showModal()"
                    +"Create Project"
                }
            }
        }
        div("empty-state-card") {
            h2("empty-state-card__heading") { +"Browse community ideas" }
            p("empty-state-card__body") { +"Explore projects shared by the community and import them to get started quickly." }
            div("empty-state-card__actions") {
                a(classes = "btn btn--secondary") {
                    href = "/ideas"
                    +"Browse Ideas"
                }
            }
        }
    }
}

private fun kotlinx.html.FlowContent.createProjectModal(worldId: Int, view: String = "execute") {
    modalForm(
        id = "create-project-modal",
        title = "Create Project",
        action = "/worlds/$worldId/projects",
        hxTarget = "#project-card-list",
        hxSwap = "afterbegin",
        errorTarget = ".form-error"
    ) {
        input {
            id = "first-project-flag"
            type = InputType.hidden
            name = "first_project"
            value = ""
        }
        input {
            id = "create-project-view"
            type = InputType.hidden
            name = "view"
            value = view
        }
        label {
            htmlFor = "create-project-name"
            +"Project Name"
            span("required-indicator") { +"*" }
        }
        input(classes = "form-control") {
            id = "create-project-name"
            type = InputType.text
            name = "name"
            placeholder = "My awesome build"
            maxLength = "100"
            minLength = "3"
            required = true
        }
        p("form-error") {
            id = "validation-error-name"
        }

        label {
            htmlFor = "create-project-description"
            +"Description"
        }
        textArea(classes = "form-control") {
            id = "create-project-description"
            name = "description"
            maxLength = "500"
            placeholder = "A brief description of the project"
        }
        p("form-error") {
            id = "validation-error-description"
        }

        label {
            htmlFor = "create-project-type"
            +"Type"
            span("required-indicator") { +"*" }
        }
        select(classes = "form-control") {
            id = "create-project-type"
            name = "type"
            required = true
            ProjectType.entries.forEach { type ->
                option {
                    value = type.name
                    +type.name.lowercase().replaceFirstChar { it.uppercase() }
                }
            }
        }
        p("form-error") {
            id = "validation-error-type"
        }

        div("modal__actions") {
            button {
                classes = setOf("btn", "btn--primary")
                type = ButtonType.submit
                attributes["onclick"] =
                    "document.getElementById('create-project-view').value = new URLSearchParams(window.location.search).get('view') || 'execute'"
                +"Create Project"
            }
            button {
                classes = setOf("btn", "btn--ghost")
                type = ButtonType.button
                attributes["onclick"] = "this.closest('dialog')?.close()"
                +"Cancel"
            }
        }
    }
}

fun projectCardFragment(worldId: Int, project: ProjectListItem): String {
    return kotlinx.html.stream.createHTML().div {
        projectCard(worldId, project)
    }
}

fun planProjectCardFragment(worldId: Int, project: ProjectPlanListItem): String {
    return kotlinx.html.stream.createHTML().div {
        planProjectCard(worldId, project)
    }
}

fun kotlinx.html.FlowContent.projectsToolbar(worldId: Int, view: String = "execute") {
    div("projects-toolbar") {
        button {
            classes = setOf("btn", "btn--primary")
            attributes["onclick"] =
                "document.getElementById('first-project-flag').value=''; document.getElementById('create-project-modal')?.showModal()"
            +"New Project"
        }
        planExecuteToggle(worldId, view)
    }
}

fun projectsToolbarOobFragment(worldId: Int, view: String = "execute"): String {
    return kotlinx.html.stream.createHTML().div {
        id = "projects-toolbar-slot"
        attributes["hx-swap-oob"] = "innerHTML"
        projectsToolbar(worldId, view)
    }
}
