package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.*
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.dangerzone.dangerZone
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.layout.alert.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

fun DIV.projectSettingsTab(user: TokenProfile, project: Project, worldMemberRole: Role) {
    classes += "project-settings-tab"

    section("project-settings-form") {
        h2 {
            + "Project Settings"
        }
        p("subtle") {
            + "Manage your project settings and preferences"
        }
        if (user.isDemoUserInProduction) {
            p("alert alert--info") {
                + "You are currently using a demo account. Changes made here will not be saved."
            }
        }
        form {
            projectNameForm(project, user)
        }
        form {
            projectDescriptionForm(project, user)
        }
        form {
            projectTypeForm(project, user)
        }
    }
    if (worldMemberRole.isHigherThanOrEqualTo(Role.ADMIN) && !user.isDemoUserInProduction) {
        dangerZone(description = "Permanently delete this project and all associated data. This action cannot be undone.") {
            dangerButton("Delete Project") {
                buttonBlock = {
                    hxDeleteWithConfirm(
                        url = Link.Worlds.world(project.worldId).project(project.id).to,
                        title = "Delete Project",
                        description = "Are you sure you want to delete the project \"${project.name}\"? This action cannot be undone.",
                        warning = "⚠️ The project, along with tasks, it's place in roadmaps and dependency trees will vanish. You might want to mark it as completed or archived instead.",
                        confirmText = project.name
                    )
                    type = ButtonType.button
                }
                iconLeft = Icons.DELETE
                iconSize = IconSize.SMALL
            }
        }
    }
}

fun FORM.projectNameForm(project: Project, user: TokenProfile) {
    encType = FormEncType.applicationXWwwFormUrlEncoded

    if (!user.isDemoUserInProduction) {
        hxTargetError(".validation-error-message")
        hxTarget("#$ALERT_CONTAINER_ID")
        hxSwap("afterbegin")
        hxPut(Link.Worlds.world(project.worldId).project(project.id).to + "/name")
        hxTrigger("input changed delay:500ms from:#project-name-input, submit")
    } else {
        onSubmit = "return false;"
    }

    classes += "project-name-form"
    label {
        htmlFor = "project-name-input"
        + "Project Name"
        span("required-indicator") { +"*" }
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
    p("validation-error-message") {
        id = "validation-error-name"
    }
}

fun FORM.projectDescriptionForm(project: Project, user: TokenProfile) {
    encType = FormEncType.applicationXWwwFormUrlEncoded

    if (!user.isDemoUserInProduction) {
        hxTargetError(".validation-error-message")
        hxTarget("#$ALERT_CONTAINER_ID")
        hxSwap("afterbegin")
        hxPut(Link.Worlds.world(project.worldId).project(project.id).to + "/description")
        hxTrigger("input changed delay:500ms from:#project-description-input, submit")
    } else {
        onSubmit = "return false;"
    }

    classes += "project-description-form"
    label {
        htmlFor = "project-description-input"
        + "Project Description"
    }
    input {
        id = "project-description-input"
        name = "description"
        value = project.description
        maxLength = "1000"
        type = InputType.text
    }
    p("validation-error-message") {
        id = "validation-error-description"
    }
}

fun FORM.projectTypeForm(project: Project, user: TokenProfile) {
    encType = FormEncType.applicationXWwwFormUrlEncoded

    if (!user.isDemoUserInProduction) {
        hxTargetError(".validation-error-message")
        hxTarget("#$ALERT_CONTAINER_ID")
        hxSwap("afterbegin")
        hxPut(Link.Worlds.world(project.worldId).project(project.id).to + "/type")
        hxTrigger("change changed delay:500ms from:#project-type-select, submit")
    } else {
        onSubmit = "return false;"
    }

    classes += "project-type-form"
    label {
        htmlFor = "project-type-select"
        + "Project Type"
        span("required-indicator") { +"*" }
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
    p("validation-error-message") {
        id = "validation-error-type"
    }
}