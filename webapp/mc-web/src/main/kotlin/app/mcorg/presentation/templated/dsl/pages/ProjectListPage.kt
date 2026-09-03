package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.project.ProjectListItem
import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.hxIndicator
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTargetError
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.emptyStateCards
import app.mcorg.presentation.templated.dsl.ResumeHeroData
import app.mcorg.presentation.templated.dsl.ResumeSort
import app.mcorg.presentation.templated.dsl.fieldLogSections
import app.mcorg.presentation.templated.dsl.modalForm
import app.mcorg.presentation.templated.dsl.newProjectMenu
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.WorldTab
import app.mcorg.presentation.templated.dsl.worldBar
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.html.h1
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.dialog
import kotlinx.html.form
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
    isWorldAdmin: Boolean = false,
    edges: List<ProjectResourceEdge> = emptyList(),
    resume: ResumeHeroData? = null,
): String = pageShell(
    pageTitle = "Seam — ${world.name}",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/modal.css",
        "/static/styles/components/callout.css",
        "/static/styles/components/resource-row.css",
        "/static/styles/components/progress.css",
        "/static/styles/components/project-card.css",
        "/static/styles/pages/project-list.css",
        "/static/styles/components/form.css",
        "/static/styles/components/item-search.css",
        "/static/styles/components/item-glyph.css",
        "/static/styles/components/world-tabs.css",
        // Both the "+ New project" menu and the empty state's doors are .np-menu__door; the
        // menu moved out of this page's own stylesheet with MCO-474 and the link did not
        // follow it, which left every create affordance here unstyled.
        "/static/styles/components/np-menu.css",
        "/static/styles/components/empty-state.css",
    ),
    scripts = listOf("/static/scripts/np-menu.js", "/static/scripts/farm-modal.js")
) {
    appHeader(
        worldName = world.name,
        worldId = world.id,
        user = user,
        isWorldAdmin = isWorldAdmin,
        breadcrumbBlock = {
            link("Worlds", "/worlds").current(world.name)
        }
    )
    main {
        container {
            // An empty world has nothing to add *to* yet, and its own empty state already
            // carries the same doors — so the menu appears only once there is a list.
            worldBar(world.id, WorldTab.PROJECTS) {
                newProjectAffordance(world.id, showMenu = projects.isNotEmpty())
            }
            div {
                id = "projects-content"
                projectsContent(user, world, projects, edges, resume)
            }
        }
    }
}

private val fieldLogDateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

fun kotlinx.html.FlowContent.fieldLogTitle(world: World) {
    div("fl-title") {
        h1("fl-title__name") { +world.name }
        div("fl-title__meta") {
            +"Field log · ${ZonedDateTime.now().format(fieldLogDateFormat)} · MC ${world.version}"
        }
    }
}

fun kotlinx.html.FlowContent.projectsContent(
    user: TokenProfile,
    world: World,
    projects: List<ProjectListItem>,
    edges: List<ProjectResourceEdge> = emptyList(),
    resume: ResumeHeroData? = null,
) {
    fieldLogTitle(world)
    div {
        id = "projects-view"
        projectsViewContent(world, projects, edges, resume)
    }
}

fun kotlinx.html.FlowContent.projectsViewContent(
    world: World,
    projects: List<ProjectListItem>,
    edges: List<ProjectResourceEdge> = emptyList(),
    resume: ResumeHeroData? = null,
) {
    if (projects.isEmpty()) {
        worldEmptyState(world.id)
    }

    fieldLogSections(world.id, projects, edges, resume)
}

/**
 * What a world with no projects shows — **on every one of its tabs**, not just this one.
 *
 * A world is empty in exactly one way, so it should answer the question in exactly one way. The
 * roadmap used to carry an empty state of its own whose only offer was a link back here, which
 * made the page a world opens on a waiting room for the page with the doors. Both roadmap views
 * now render this block instead (`roadmapPage`, `roadmapGraphPage`) — which is why it lives next
 * to [newProjectAffordance]: the doors below open that function's dialogs, so a page rendering
 * one must render the other, and link `np-menu.css` for `.np-menu__door`.
 */
fun kotlinx.html.FlowContent.worldEmptyState(worldId: Int) {
    emptyStateCards(id = "projects-empty-state") {
        div("empty-state-card") {
            h2("empty-state-card__heading") { +"Plan your own project" }
            p("empty-state-card__body") { +"Create a new project and start tracking your builds and resources." }
            // Same "pick a door" affordance as the populated state's "+ New project" menu
            // (NewProjectMenu.kt) — reuses .np-menu__door so the empty and populated create
            // entry points read as the same control, and adds the schematic door that was
            // previously only reachable after creating an empty project.
            div("empty-state-card__doors") {
                button(classes = "np-menu__door") {
                    type = ButtonType.button
                    attributes["onclick"] =
                        "document.getElementById('schematic-project-modal')?.showModal()"
                    span("np-menu__door-glyph") { +"⤓" }
                    span("np-menu__door-text") {
                        span("np-menu__door-title") { +"From a schematic" }
                        span("np-menu__door-sub") { +".litematic" }
                    }
                }
                button(classes = "np-menu__door") {
                    type = ButtonType.button
                    attributes["onclick"] =
                        "document.getElementById('create-project-modal')?.showModal()"
                    span("np-menu__door-glyph") { +"+" }
                    span("np-menu__door-text") {
                        span("np-menu__door-title") { +"Blank project" }
                        span("np-menu__door-sub") { +"name it, fill it later" }
                    }
                }
                // A world usually starts with farms that already exist — the empty state
                // is the most likely place to record them (MCO-298).
                button(classes = "np-menu__door") {
                    type = ButtonType.button
                    attributes["onclick"] =
                        "document.getElementById('record-farm-modal')?.showModal()"
                    span("np-menu__door-glyph") { +"⚙" }
                    span("np-menu__door-text") {
                        span("np-menu__door-title") { +"Record an existing farm" }
                        span("np-menu__door-sub") { +"already built, already producing" }
                    }
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

private fun kotlinx.html.FlowContent.schematicProjectModal(worldId: Int) {
    dialog {
            id = "schematic-project-modal"
            classes = setOf("modal-backdrop")
            div("modal") {
                div("modal__heading") { +"From a schematic" }
                div("modal__body") {
                    // A plain multipart POST, not an HTMX swap: the upload now leads to the
                    // review page (MCO-303), which is a page of its own rather than a
                    // fragment — the browser navigating there is the whole mechanism.
                    form {
                        method = kotlinx.html.FormMethod.post
                        action = "/worlds/$worldId/projects/from-schematic/review"
                        encType = kotlinx.html.FormEncType.multipartFormData
                        attributes["onsubmit"] =
                            "document.getElementById('schematic-project-progress')?.classList.add('is-uploading')"

                        label {
                            htmlFor = "schematic-project-file"
                            +"Schematic files"
                            span("required-indicator") { +"*" }
                        }
                        input(classes = "form-control") {
                            id = "schematic-project-file"
                            type = InputType.file
                            name = "schematicFile"
                            accept = ".litematic"
                            required = true
                            // Litematica saves a selection from one world, so a build with a
                            // nether side is two files (MCO-414). They import as one project.
                            multiple = true
                        }
                        p("form-help-text") {
                            +"Pick several if the build spans dimensions — they become one project."
                        }
                        p("form-error") {
                            id = "validation-error-schematicFile"
                        }

                        label {
                            htmlFor = "schematic-project-name"
                            +"Project name"
                        }
                        input(classes = "form-control") {
                            id = "schematic-project-name"
                            type = InputType.text
                            name = "name"
                            placeholder = "Defaults to the schematic's name"
                            maxLength = "100"
                        }
                        p("form-error") {
                            id = "validation-error-name-schematic"
                        }

                        // Upload/parse feedback: revealed by the form's onsubmit (.is-uploading
                        // in modal.css) — large schematics take a while to parse and the page
                        // does not navigate until they have.
                        div("modal__progress htmx-indicator") {
                            id = "schematic-project-progress"
                            div("modal__progress-spinner") {}
                            span { +"Parsing schematic…" }
                        }

                        div("modal__actions") {
                            button {
                                classes = setOf("btn", "btn--primary")
                                type = ButtonType.submit
                                +"Create from schematic"
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
            }
    }
}

/**
 * MCO-298 — "record an existing farm". Its own door and its own modal because it does
 * something the create-project form cannot: the project is created operational
 * (`COMPLETED`/`DONE`) with its produced items, so it supplies every other project's
 * gathering plan the moment it exists.
 *
 * Produced items are staged client-side (farm-modal.js) as `productions[<itemId>]`
 * hidden inputs — the project has no id to post them against yet, so the productions
 * panel from MCO-297 cannot be reused here; it takes over once the project exists.
 *
 * Field names are farm-prefixed: validation errors come back as out-of-band swaps keyed
 * by `validation-error-<parameter>`, and the create-project modal on this same page
 * already owns `validation-error-name`.
 */
private fun kotlinx.html.FlowContent.recordFarmModal(worldId: Int) {
    dialog {
        id = "record-farm-modal"
        classes = setOf("modal-backdrop")
        div("modal") {
            div("modal__heading") { +"Record an existing farm" }
            div("modal__body") {
                p("modal__description") {
                    +"A farm already standing in your world. It is recorded as done and producing, so its output counts as supply in every project's gathering plan."
                }
                form {
                    id = "record-farm-form"
                    hxPost("/worlds/$worldId/projects/farm")
                    hxTarget("#projects-view")
                    hxSwap("afterbegin")
                    hxTargetError(".form-error")
                    // htmx events bubble: the item search inside this form fires
                    // afterRequest too, and without the target check every keystroke's
                    // search response would close the modal.
                    attributes["hx-on::after-request"] =
                        "if(event.target === this && event.detail.successful) { window.resetFarmModal(this) }"

                    label {
                        htmlFor = "record-farm-name"
                        +"Farm name"
                        span("required-indicator") { +"*" }
                    }
                    input(classes = "form-control") {
                        id = "record-farm-name"
                        type = InputType.text
                        name = "farmName"
                        placeholder = "Iron farm"
                        maxLength = "100"
                        minLength = "3"
                        required = true
                    }
                    p("form-error") { id = "validation-error-farmName" }

                    label {
                        htmlFor = "record-farm-description"
                        +"Notes"
                    }
                    textArea(classes = "form-control") {
                        id = "record-farm-description"
                        name = "farmDescription"
                        maxLength = "500"
                        placeholder = "Anything worth remembering — design, quirks, who built it"
                    }
                    p("form-error") { id = "validation-error-farmDescription" }

                    label {
                        htmlFor = "record-farm-type"
                        +"Type"
                    }
                    select(classes = "form-control") {
                        id = "record-farm-type"
                        name = "farmType"
                        ProjectType.entries.forEach { type ->
                            option {
                                value = type.name
                                selected = type == ProjectType.FARMING
                                +type.name.lowercase().replaceFirstChar { it.uppercase() }
                            }
                        }
                    }

                    label { +"Location" }
                    div("farm-location-row") {
                        input(classes = "form-control") {
                            id = "record-farm-x"
                            type = InputType.number
                            name = "farmX"
                            placeholder = "X"
                            attributes["aria-label"] = "X coordinate"
                        }
                        input(classes = "form-control") {
                            id = "record-farm-y"
                            type = InputType.number
                            name = "farmY"
                            placeholder = "Y"
                            attributes["aria-label"] = "Y coordinate"
                        }
                        input(classes = "form-control") {
                            id = "record-farm-z"
                            type = InputType.number
                            name = "farmZ"
                            placeholder = "Z"
                            attributes["aria-label"] = "Z coordinate"
                        }
                        select(classes = "form-control") {
                            id = "record-farm-dimension"
                            name = "farmDimension"
                            attributes["aria-label"] = "Dimension"
                            Dimension.entries.forEach { dimension ->
                                option {
                                    value = dimension.name
                                    +dimension.name.lowercase().replaceFirstChar { it.uppercase() }
                                }
                            }
                        }
                    }
                    p("form-help-text") { +"Optional — leave empty if you would rather not pin it down." }
                    p("form-error") { id = "validation-error-farmLocation" }

                    label {
                        +"Produces"
                        span("required-indicator") { +"*" }
                    }
                    div("item-search-combo") {
                        div("item-search-field") {
                            input(type = InputType.text, classes = "form-control") {
                                id = "record-farm-item-input"
                                placeholder = "Search items by name..."
                                autoComplete = "off"
                                attributes["hx-get"] = "/items/search"
                                attributes["hx-trigger"] = "input changed delay:300ms"
                                attributes["hx-target"] = "#record-farm-item-results"
                                attributes["hx-swap"] = "innerHTML"
                                attributes["hx-vals"] = "js:{q: this.value}"
                            }
                            div("item-search-results") { id = "record-farm-item-results" }
                        }
                        input(type = InputType.hidden) { id = "record-farm-selected-item-id" }
                        span("item-selected-label") { id = "record-farm-selected-item-label" }
                    }
                    div("item-add-row") {
                        div {
                            label { htmlFor = "record-farm-rate"; +"Rate per hour" }
                            input(type = InputType.number, classes = "form-control") {
                                id = "record-farm-rate"
                                min = "0"
                                placeholder = "unknown"
                            }
                        }
                        button(classes = "btn btn--secondary btn--sm") {
                            type = ButtonType.button
                            attributes["onclick"] = "window.addFarmProduction()"
                            +"Add item"
                        }
                    }
                    div("farm-production-list") { id = "farm-production-list" }
                    p("form-error") { id = "validation-error-productions" }

                    div("modal__actions") {
                        button {
                            classes = setOf("btn", "btn--primary")
                            type = ButtonType.submit
                            +"Record farm"
                        }
                        button {
                            classes = setOf("btn", "btn--ghost")
                            type = ButtonType.button
                            attributes["onclick"] = "window.resetFarmModal(document.getElementById('record-farm-form'))"
                            +"Cancel"
                        }
                    }
                }
            }
        }
    }
}

private fun kotlinx.html.FlowContent.createProjectModal(worldId: Int) {
    // The Field Log groups projects by state and reloads via HX-Redirect, so this only
    // needs a target that exists.
    modalForm(
        id = "create-project-modal",
        title = "Create Project",
        action = "/worlds/$worldId/projects",
        hxTarget = "#projects-view",
        hxSwap = "afterbegin",
        errorTarget = ".form-error"
    ) {
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

/**
 * The "+ New project" menu together with the three dialogs its doors open.
 *
 * They travel as one unit: each door calls `showModal()` on a specific `<dialog>`, so rendering
 * the menu on a page without them gives you doors that silently do nothing. The empty state's
 * cards open the same dialogs, which is why they are rendered unconditionally.
 */
fun kotlinx.html.FlowContent.newProjectAffordance(worldId: Int, showMenu: Boolean = true) {
    if (showMenu) newProjectMenu(worldId)
    createProjectModal(worldId)
    schematicProjectModal(worldId)
    recordFarmModal(worldId)
}

