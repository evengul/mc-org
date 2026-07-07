package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.isDemoUserInProduction
import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.world.commonsteps.WorldProjectPeek
import app.mcorg.presentation.templated.dsl.Link
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.badgeModifier
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.label
import app.mcorg.presentation.templated.dsl.lucide
import app.mcorg.presentation.templated.dsl.modalForm
import app.mcorg.presentation.templated.dsl.pageHeading
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.progressBar
import app.mcorg.presentation.templated.dsl.components.pendingInvitationsSection
import app.mcorg.presentation.templated.utils.formatAsCompactRelative
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.b
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.textArea
import kotlinx.html.ul

private fun World.progressPercent(): Int =
    if (totalProjects > 0) (completedProjects.coerceAtMost(totalProjects) * 100) / totalProjects else 0

fun worldListPage(
    user: TokenProfile,
    worlds: List<World>,
    supportedVersions: List<MinecraftVersion.Release>,
    pendingInvitations: List<Invite> = emptyList(),
    heroPeek: List<WorldProjectPeek> = emptyList(),
): String = pageShell(
    pageTitle = "Seam — Worlds",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/common.css",
        "/static/styles/components/callout.css",
        "/static/styles/components/progress.css",
        "/static/styles/components/form.css",
        "/static/styles/components/pending-invitations.css",
        "/static/styles/pages/worlds.css",
    ),
    scripts = listOf("/static/scripts/worlds.js"),
) {
    appHeader(user = user)
    main {
        container {
            div { id = "notice-container" }
            div {
                id = "worlds-content"
                worldsContent(user, worlds, supportedVersions, pendingInvitations, heroPeek)
            }
        }
    }
}

fun FlowContent.worldsContent(
    user: TokenProfile,
    worlds: List<World>,
    supportedVersions: List<MinecraftVersion.Release>,
    pendingInvitations: List<Invite> = emptyList(),
    heroPeek: List<WorldProjectPeek> = emptyList(),
    justCreatedWorldId: Int? = null,
) {
    div("worlds-main") {
        pendingInvitationsSection(pendingInvitations)

        if (worlds.isEmpty()) {
            worldsEmptyState(user, supportedVersions)
        } else {
            val justCreated = justCreatedWorldId?.let { id -> worlds.firstOrNull { it.id == id } }
            if (justCreated != null) {
                div("callout callout--success worlds-callout") {
                    attributes["role"] = "note"
                    span("callout__icon") {
                        attributes["aria-hidden"] = "true"
                        +"✓"
                    }
                    div("callout__body") {
                        span("callout__lead") { +"${justCreated.name} is ready." }
                        +" Open it to plan your first project."
                    }
                }
            }

            div("worlds-head") {
                pageHeading("Worlds", "${worlds.size} world${if (worlds.size == 1) "" else "s"}")
                newWorldButton(user, primary = false, label = "New world")
            }

            val hero = worlds.first()
            worldHero(
                world = hero,
                peek = heroPeek,
                fresh = hero.id == justCreatedWorldId,
                lead = worlds.size > 1,
            )

            val others = worlds.drop(1)
            if (others.isNotEmpty()) {
                div("section-label worlds-subsection") { +"Other worlds" }
                div("worlds-roster") {
                    others.forEach { worldRow(it) }
                }
            }
        }

        createWorldModal(user, supportedVersions)
    }
}

private fun FlowContent.worldsEmptyState(
    user: TokenProfile,
    supportedVersions: List<MinecraftVersion.Release>,
) {
    div("worlds-empty") {
        div("world-glyph") { lucide("box", 34) }
        h1("worlds-empty__title") { +"No worlds yet" }
        p("worlds-empty__text") {
            +"A "
            b { +"world" }
            +" in Seam mirrors one of your Minecraft worlds — a singleplayer save or a server."
        }
        newWorldButton(user, primary = true, label = "Create world")
        div("worlds-features") {
            worldsFeature("layers", "Plan projects & tasks")
            worldsFeature("route", "Generate resource paths")
            worldsFeature("lightbulb", "Bank ideas for later")
        }
    }
}

private fun FlowContent.worldsFeature(icon: String, text: String) {
    div("worlds-feature") {
        lucide(icon, 20)
        span { +text }
    }
}

private fun FlowContent.newWorldButton(user: TokenProfile, primary: Boolean, label: String) {
    if (user.isDemoUserInProduction()) {
        button(classes = "btn ${if (primary) "btn--primary" else "btn--secondary"}") {
            type = ButtonType.button
            disabled = true
            +label
        }
        p("worlds-demo-notice") {
            +"Demo users cannot create worlds. Please register for a full account."
        }
    } else {
        button(classes = "btn ${if (primary) "btn--primary" else "btn--secondary"}") {
            type = ButtonType.button
            attributes["onclick"] = "document.getElementById('create-world-modal')?.showModal()"
            lucide("plus", 15)
            +label
        }
    }
}

private fun FlowContent.worldHero(world: World, peek: List<WorldProjectPeek>, fresh: Boolean, lead: Boolean) {
    val classes = buildString {
        append("world-hero")
        if (fresh) append(" world-hero--fresh")
        if (lead) append(" world-hero--lead")
    }
    div(classes) {
        div("world-hero__main") {
            div("world-identity") {
                if (!fresh) pinButton(world)
                span("world-name") { +world.name }
                span("badge badge--neutral") { +world.version.toString() }
            }

            if (fresh) {
                div("world-meta world-hero__submeta") { +"created just now" }
                div("world-hero__empty") {
                    lucide("clock", 15)
                    span { +"No projects yet — plan your first build." }
                }
            } else {
                val opened = world.lastOpenedAt?.formatAsCompactRelative()
                div("world-hero__meta world-meta") {
                    +(if (opened != null) "opened $opened" else "not opened yet")
                }
                div("world-hero__body") {
                    if (world.totalProjects == 0) {
                        div("world-hero__empty") {
                            lucide("clock", 15)
                            span { +"No projects yet — plan your first build." }
                        }
                        openWorldButton(world)
                    } else if (lead) {
                        div("world-hero__body--row") {
                            div("world-hero__progress-block") {
                                div("world-stat") {
                                    span("world-stat__count") { +"${world.totalProjects} projects · ${world.progressPercent()}%" }
                                }
                                progressBar(world.completedProjects, world.totalProjects, large = true)
                            }
                            openWorldButton(world)
                        }
                    } else {
                        div("world-stat") {
                            span("world-stat__count") { +"${world.totalProjects} project${if (world.totalProjects == 1) "" else "s"}" }
                            span("world-stat__pct") { +"· ${world.progressPercent()}% overall" }
                        }
                        div("world-progress") {
                            progressBar(world.completedProjects, world.totalProjects, large = true)
                        }
                        openWorldButton(world)
                    }
                }
            }
        }
        div("world-hero__aside") {
            if (fresh) {
                div("world-hero__actions") {
                    a(href = Link.Worlds.world(world.id).projects().to, classes = "btn btn--primary") {
                        lucide("arrow-right", 15)
                        +"Open & add first project"
                    }
                    a(href = Link.Ideas.to, classes = "btn btn--secondary") { +"Open Ideas bank" }
                }
            } else {
                div("section-label") { +"Active projects" }
                if (peek.isEmpty()) {
                    div("world-hero__peek-empty") { +"No active projects" }
                } else {
                    peek.forEach { item ->
                        div("world-peek__row") {
                            span("world-peek__name") { +item.name }
                            span("badge ${item.state.badgeModifier}") { +item.state.label }
                        }
                    }
                }
            }
        }
    }
}

private fun FlowContent.worldRow(world: World) {
    div("world-row") {
        pinButton(world)
        a(href = Link.Worlds.world(world.id).projects().to, classes = "world-row__link") {
            div("world-row__id") {
                div("world-name") { +world.name }
                div("world-meta") { +world.version.toString() }
            }
            div("world-row__progress") {
                progressBar(world.completedProjects, world.totalProjects, large = false)
                span("world-row__pct") { +"${world.totalProjects} proj · ${world.progressPercent()}%" }
            }
            span("world-row__time") { +(world.lastOpenedAt?.formatAsCompactRelative() ?: "never") }
            lucide("arrow-right", 16, false, "world-row__go")
        }
    }
}

private fun FlowContent.pinButton(world: World) {
    button(classes = "pin-btn${if (world.pinned) " pin-btn--on" else ""}") {
        type = ButtonType.button
        attributes["hx-post"] = "/worlds/pin/${world.id}"
        attributes["hx-target"] = "#worlds-content"
        attributes["hx-swap"] = "outerHTML"
        attributes["aria-pressed"] = world.pinned.toString()
        attributes["aria-label"] = if (world.pinned) "Unpin ${world.name}" else "Pin ${world.name}"
        lucide("star", 16, filled = world.pinned)
    }
}

private fun FlowContent.openWorldButton(world: World) {
    a(href = Link.Worlds.world(world.id).projects().to, classes = "btn btn--primary") {
        lucide("arrow-right", 15)
        +"Open world"
    }
}

private fun FlowContent.createWorldModal(
    user: TokenProfile,
    supportedVersions: List<MinecraftVersion.Release>,
) {
    modalForm(
        id = "create-world-modal",
        title = "Create world",
        action = "/worlds",
        hxTarget = "#worlds-content",
        hxSwap = "outerHTML",
        errorTarget = ".form-error",
    ) {
        p("worlds-modal-intro") { +"Match it to a Minecraft save or server. You can rename it later." }

        if (user.isDemoUserInProduction()) {
            p("form-error") {
                +"Demo users cannot create worlds. Please register for a full account."
            }
        }

        label("form-label") {
            htmlFor = "create-world-name"
            +"World name"
        }
        input(classes = "form-control") {
            id = "create-world-name"
            type = InputType.text
            name = "name"
            placeholder = "e.g. Survival, SkyBlock SMP"
            maxLength = "100"
            minLength = "3"
            required = true
            if (user.isDemoUserInProduction()) disabled = true
        }
        p("form-error") { id = "validation-error-name" }

        versionField(supportedVersions, disabled = user.isDemoUserInProduction())
        p("form-error") { id = "validation-error-version" }

        label("form-label form-label--optional") {
            htmlFor = "create-world-description"
            +"Description · optional"
        }
        textArea(classes = "form-control form-control--optional") {
            id = "create-world-description"
            name = "description"
            maxLength = "500"
            placeholder = "short note about this world"
            if (user.isDemoUserInProduction()) disabled = true
        }
        p("form-error") { id = "validation-error-description" }

        div("modal__actions") {
            button(classes = "btn btn--ghost") {
                type = ButtonType.button
                attributes["onclick"] = "this.closest('dialog')?.close()"
                +"Cancel"
            }
            button(classes = "btn btn--primary") {
                type = ButtonType.submit
                if (user.isDemoUserInProduction()) disabled = true
                +"Create world"
            }
        }
    }
}

/**
 * Custom dropdown over the fixed set of supported versions. A hidden input carries the
 * value for the form POST; worlds.js toggles the menu and updates value + label. Falls
 * back to the first (latest) version as the initial selection.
 */
private fun FlowContent.versionField(supportedVersions: List<MinecraftVersion.Release>, disabled: Boolean) {
    val default = supportedVersions.firstOrNull()?.toString() ?: ""
    div("version-field") {
        label("form-label") {
            id = "ver-label"
            +"Minecraft version"
        }
        div("version-control") {
            div("form-control version-select") {
                attributes["role"] = "button"
                attributes["tabindex"] = if (disabled) "-1" else "0"
                attributes["aria-haspopup"] = "listbox"
                attributes["aria-expanded"] = "false"
                attributes["aria-labelledby"] = "ver-label"
                attributes["data-version-select"] = ""
                span("version-select__value") { +default }
                lucide("chevron-down", 16)
            }
            ul("version-menu") {
                attributes["role"] = "listbox"
                attributes["aria-labelledby"] = "ver-label"
                attributes["hidden"] = "true"
                supportedVersions.forEachIndexed { index, version ->
                    val v = version.toString()
                    li("version-menu__row${if (index == 0) " version-menu__row--selected" else ""}") {
                        attributes["role"] = "option"
                        attributes["tabindex"] = "-1"
                        attributes["aria-selected"] = (index == 0).toString()
                        attributes["data-version"] = v
                        +v
                        if (index == 0) lucide("check", 15)
                    }
                }
            }
        }
        input(classes = "version-input") {
            type = InputType.hidden
            name = "version"
            value = default
            attributes["data-version-input"] = ""
        }
        p("form-help-text") { +"Required — the path generator uses this to pick valid recipes." }
    }
}
