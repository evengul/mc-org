package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.resources.StorageTracking
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.dsl.Link
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.dangerZone
import app.mcorg.presentation.templated.dsl.fieldError
import app.mcorg.presentation.templated.dsl.pageHeading
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.section
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.InputType
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.strong

data class ProjectSettingsData(
    val project: Project,
    val worldName: String,
    val isWorldAdmin: Boolean,
    val tracking: StorageTracking,
)

const val STORAGE_TRACKING_SECTION_ID = "project-storage-tracking"

/**
 * A project's settings: what changes how the project behaves, rather than what it is.
 *
 * The name, state and location stay inline in the project's header, where they are edited often
 * and read every time. This page holds what is set once and then left alone (MCO-540), and the
 * delete, which used to sit beside the name, one click from a mistake.
 */
fun projectSettingsPage(user: TokenProfile, data: ProjectSettingsData): String = pageShell(
    pageTitle = "Seam — ${data.project.name} Settings",
    user = user,
) {
    val project = data.project
    appHeader(
        worldName = data.worldName,
        worldId = project.worldId,
        user = user,
        breadcrumbBlock = {
            link("Worlds", "/worlds")
                .link(data.worldName, Link.Worlds.world(project.worldId).roadmap().to)
                .link(project.name, Link.Worlds.world(project.worldId).project(project.id).to)
                .current("Settings")
        }
    )
    main {
        container {
            a(classes = "btn btn--ghost btn--sm project-settings__back") {
                href = Link.Worlds.world(project.worldId).project(project.id).to
                +"← Back to ${project.name}"
            }
            pageHeading(title = "Project Settings", subtitle = project.name)
            div("settings-page__sections") {
                section(
                    title = "Chest counts",
                    subtitle = "Where this project's gathered counts come from",
                    card = true,
                ) {
                    storageTrackingSection(project.worldId, project.id, data.tracking)
                }
                if (data.isWorldAdmin) {
                    dangerZone(
                        description = "Once you delete a project, there is no going back. Its tasks, resources and progress are deleted with it.",
                    ) {
                        button(classes = "btn btn--danger") {
                            type = ButtonType.button
                            hxDeleteWithConfirm(
                                url = Link.Worlds.world(project.worldId).project(project.id).to,
                                title = "Delete project",
                                description = "This action cannot be undone. All tasks, resources, and progress for this project will be permanently deleted.",
                                warning = "Warning: This will permanently delete \"${project.name}\" and all associated data.",
                                confirmText = project.name,
                            )
                            +"Delete project"
                        }
                    }
                }
            }
        }
    }
}

fun storageTrackingSectionHtml(worldId: Int, projectId: Int, tracking: StorageTracking): String =
    createHTML().div {
        storageTrackingSection(worldId, projectId, tracking)
    }.removePrefix("<div>").removeSuffix("</div>")

/**
 * The Storage-tracked switch (MCO-540), named for what it does rather than for the mechanism.
 *
 * One button, not a checkbox: the two directions say different things — turning it on replaces
 * every typed count and is confirmed with exactly that; turning it off changes no number at all —
 * and a checkbox can only say "on" and "off".
 *
 * With nothing tagged the button is disabled, and the reason sits beside it rather than arriving
 * as an error after the click. The server refuses the same case regardless.
 */
private fun FlowContent.storageTrackingSection(worldId: Int, projectId: Int, tracking: StorageTracking) {
    form(classes = "settings-form project-settings__tracking") {
        id = STORAGE_TRACKING_SECTION_ID
        hxPatch(Link.Worlds.world(worldId).project(projectId).settings().to + "/storage-tracked")
        hxTarget("#$STORAGE_TRACKING_SECTION_ID")
        hxSwap("outerHTML")
        // On the form, because the form is what sends the request. Only the direction that
        // replaces numbers asks; turning it off changes none.
        if (!tracking.tracked && tracking.taggedContainers > 0) {
            attributes["hx-confirm"] =
                "Every count on this project will follow its tagged chests, and an item in none of them " +
                    "will read 0. This replaces the counts you typed. Continue?"
        }

        input(type = InputType.hidden, name = "tracked") { value = (!tracking.tracked).toString() }

        if (tracking.tracked) {
            p("project-settings__state") {
                strong { +"Counted from chests." }
                +" Every count on this project follows its ${chests(tracking.readableContainers)}; "
                +"an item in none of them counts as 0. Counts cannot be typed while this is on."
            }
            p("settings-form__helper") {
                +"Turning this off keeps every count where it is, and lets you type them again."
            }
            button(classes = "btn btn--secondary btn--sm") {
                type = ButtonType.submit
                +"Type counts by hand instead"
            }
        } else {
            p("project-settings__state") {
                strong { +"Typed by hand." }
                +" Tagged chests show what they hold beside each count, and you choose whether to use it."
            }
            if (tracking.taggedContainers == 0) {
                p("settings-form__helper") {
                    +"Tag a chest to this project in game with the Seam mod to let its chests drive the counts."
                }
                button(classes = "btn btn--primary btn--sm") {
                    type = ButtonType.submit
                    disabled = true
                    +"Count from chests"
                }
            } else {
                p("settings-form__helper") {
                    +"Once your tagging is complete, let the ${chests(tracking.taggedContainers)} drive the counts. "
                    +"Each sweep then sets every count to what the chests hold."
                }
                button(classes = "btn btn--primary btn--sm") {
                    type = ButtonType.submit
                    +"Count from chests"
                }
            }
        }
        fieldError("storageTracked")
    }
}

private fun chests(count: Int) = if (count == 1) "1 tagged chest" else "$count tagged chests"
