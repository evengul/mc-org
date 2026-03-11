package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.isDemoUserInProduction
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.emptyState
import app.mcorg.presentation.templated.dsl.modalForm
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.worldCardList
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.main
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.textArea

fun worldListPage(
    user: TokenProfile,
    worlds: List<World>,
    supportedVersions: List<MinecraftVersion.Release>
): String = pageShell(
    pageTitle = "MC-ORG — Worlds",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/world-card.css",
        "/static/styles/components/empty-state.css",
        "/static/styles/components/btn.css",
        "/static/styles/components/modal.css",
    )
) {
    appHeader(user = user)
    main {
        container {
            div {
                id = "worlds-content"
                worldsContent(user, worlds, supportedVersions)
            }
        }
    }
}

fun kotlinx.html.FlowContent.worldsContent(
    user: TokenProfile,
    worlds: List<World>,
    supportedVersions: List<MinecraftVersion.Release>
) {
    if (worlds.isEmpty()) {
        emptyState(
            heading = "No worlds yet",
            body = "Get started by creating your first Minecraft world to organize your projects."
        ) {
            createWorldModalButton(user, supportedVersions)
        }
    } else {
        div("worlds-toolbar") {
            input(classes = "form-control") {
                id = "worlds-search-input"
                type = InputType.search
                name = "query"
                placeholder = "Search worlds..."
                hxGet("/worlds/search")
                hxTarget("#world-card-list")
                hxSwap("outerHTML")
                hxTrigger("input changed delay:300ms, search")
            }
            createWorldModalButton(user, supportedVersions)
        }
        worldCardList(worlds)
    }

    createWorldModal(user, supportedVersions)
}

private fun kotlinx.html.FlowContent.createWorldModalButton(
    user: TokenProfile,
    supportedVersions: List<MinecraftVersion.Release>
) {
    if (user.isDemoUserInProduction()) {
        button {
            classes = setOf("btn", "btn--primary")
            disabled = true
            +"Create World"
        }
        p("worlds-demo-notice") {
            +"Demo users cannot create worlds. Please register for a full account."
        }
    } else {
        button {
            classes = setOf("btn", "btn--primary")
            attributes["onclick"] = "document.getElementById('create-world-modal')?.showModal()"
            +"Create World"
        }
    }
}

private fun kotlinx.html.FlowContent.createWorldModal(
    user: TokenProfile,
    supportedVersions: List<MinecraftVersion.Release>
) {
    modalForm(
        id = "create-world-modal",
        title = "Create World",
        action = "/worlds",
        hxTarget = "#worlds-content",
        hxSwap = "outerHTML",
        errorTarget = ".form-error"
    ) {
        if (user.isDemoUserInProduction()) {
            p("form-error") {
                +"Demo users cannot create worlds. Please register for a full account."
            }
        }

        label {
            htmlFor = "create-world-name"
            +"World Name"
            span("required-indicator") { +"*" }
        }
        input(classes = "form-control") {
            id = "create-world-name"
            type = InputType.text
            name = "name"
            placeholder = "My survival world"
            maxLength = "100"
            minLength = "3"
            required = true
            if (user.isDemoUserInProduction()) disabled = true
        }
        p("form-error") {
            id = "validation-error-name"
        }

        label {
            htmlFor = "create-world-description"
            +"Description"
        }
        textArea(classes = "form-control") {
            id = "create-world-description"
            name = "description"
            maxLength = "500"
            placeholder = "A brief description of the world"
            if (user.isDemoUserInProduction()) disabled = true
        }
        p("form-error") {
            id = "validation-error-description"
        }

        label {
            htmlFor = "create-world-version"
            +"Minecraft Version"
            span("required-indicator") { +"*" }
        }
        select(classes = "form-control") {
            id = "create-world-version"
            name = "version"
            required = true
            if (user.isDemoUserInProduction()) disabled = true
            supportedVersions.forEachIndexed { i, version ->
                option {
                    value = version.toString()
                    +"$version${if (i == 0) " (Latest)" else if (i == supportedVersions.size - 1) " (Earliest compatible)" else ""}"
                }
            }
        }
        p("form-error") {
            id = "validation-error-version"
        }

        div("modal__actions") {
            button {
                classes = setOf("btn", "btn--primary")
                type = kotlinx.html.ButtonType.submit
                if (user.isDemoUserInProduction()) disabled = true
                +"Create World"
            }
            button {
                classes = setOf("btn", "btn--ghost")
                type = kotlinx.html.ButtonType.button
                attributes["onclick"] = "this.closest('dialog')?.close()"
                +"Cancel"
            }
        }
    }
}
