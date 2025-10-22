package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.Role
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxPut
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.dangerzone.dangerZone
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.layout.alert.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FORM
import kotlinx.html.FormEncType
import kotlinx.html.InputType
import kotlinx.html.classes
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
            projectNameForm(project)
        }
        form {
            projectDescriptionForm(project)
        }
        form {
            projectTypeForm(project)
        }
    }
    if (worldMemberRole.isHigherThanOrEqualTo(Role.ADMIN)) {
        dangerZone(description = "Permanently delete this project and all associated data. This action cannot be undone.") {
            dangerButton("Delete Project") {
                buttonBlock = {
                    hxDelete(Link.Worlds.world(project.worldId).project(project.id).to, "Are you sure you want to delete the project \"${project.name}\"? This action cannot be undone.")
                    type = ButtonType.button
                }
                iconLeft = Icons.DELETE
                iconSize = IconSize.SMALL
            }
        }
    }
}

fun FORM.projectNameForm(project: Project) {
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#$ALERT_CONTAINER_ID")
    hxSwap("afterbegin")
    hxPut(Link.Worlds.world(project.worldId).project(project.id).to + "/name")
    hxTrigger("input changed delay:500ms from:#project-name-input, submit")

    classes += "project-name-form"
    label {
        htmlFor = "project-name-input"
        + "Project Name"
    }
    input {
        id = "project-name-input"
        name = "name"
        value = project.name
        required = true
        minLength = "3"
        maxLength = "100"
        type = InputType.text
    }
}

fun FORM.projectDescriptionForm(project: Project) {
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#$ALERT_CONTAINER_ID")
    hxSwap("afterbegin")
    hxPut(Link.Worlds.world(project.worldId).project(project.id).to + "/description")
    hxTrigger("input changed delay:500ms from:#project-description-input, submit")

    classes += "project-description-form"
    label {
        htmlFor = "project-description-input"
        + "Project Description"
    }
    input {
        id = "project-description-input"
        name = "description"
        value = project.description
        minLength = "3"
        maxLength = "1000"
        type = InputType.text
    }
}

fun FORM.projectTypeForm(project: Project) {
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#$ALERT_CONTAINER_ID")
    hxSwap("afterbegin")
    hxPut(Link.Worlds.world(project.worldId).project(project.id).to + "/type")
    hxTrigger("change changed delay:500ms from:#project-type-select, submit")

    classes += "project-type-form"
    label {
        htmlFor = "project-type-select"
        + "Project Type"
    }
    select {
        id = "project-type-select"
        name = "type"
        ProjectType.entries.forEach { type ->
            option {
                value = type.name
                selected = type == project.type
                + type.toPrettyEnumName()
            }
        }
    }
}