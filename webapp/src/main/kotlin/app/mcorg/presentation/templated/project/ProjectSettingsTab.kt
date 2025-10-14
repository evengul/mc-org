package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.Role
import app.mcorg.presentation.hxConfirm
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxPut
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FORM
import kotlinx.html.FormEncType
import kotlinx.html.InputType
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select

fun DIV.projectSettingsTab(project: Project, worldMemberRole: Role) {
    classes += "project-settings-tab"

    section("project-settings-form") {
        h2 {
            + "Project Settings"
        }
        p("subtle") {
            + "Manage your project settings and preferences"
        }
        form {
            projectSettingsForm(project)
        }
    }
    if (worldMemberRole.isHigherThanOrEqualTo(Role.ADMIN)) {
        section("danger-zone") {
            div("danger-zone-header") {
                h2("danger-zone-title") {
                    +"Danger Zone"
                }
                p("subtle") {
                    + "Permanently delete this project and all associated data. This action cannot be undone."
                }
            }
            div("danger-zone-content") {
                dangerButton("Delete Project") {
                    buttonBlock = {
                        hxDelete(Link.Worlds.world(project.worldId).project(project.id).to)
                        hxConfirm("Are you sure you want to delete the project \"${project.name}\"? This action cannot be undone.")
                        type = ButtonType.button
                    }
                    iconLeft = Icons.DELETE
                    iconSize = IconSize.SMALL
                }
            }
        }
    }
}

fun FORM.projectSettingsForm(project: Project) {
    encType = FormEncType.applicationXWwwFormUrlEncoded
    hxPut(Link.Worlds.world(project.worldId).project(project.id).to + "/metadata")
    classes += "project-settings-form"
    label {
        htmlFor = "project-settings-name"
        + "Project Name"
    }
    input {
        id = "project-settings-name"
        name = "name"
        value = project.name
        required = true
        minLength = "3"
        maxLength = "100"
        type = InputType.text
    }
    label {
        htmlFor = "project-settings-description"
        + "Description"
    }
    input {
        id = "project-settings-description"
        name = "description"
        value = project.description
        maxLength = "1000"
        type = InputType.text
    }
    label {
        htmlFor = "project-settings-type"
    }
    select {
        id = "project-settings-type"
        name = "type"
        ProjectType.entries.forEach { type ->
            option {
                value = type.name
                selected = type == project.type
                + type.toPrettyEnumName()
            }
        }
    }
    actionButton("Save Changes")
}