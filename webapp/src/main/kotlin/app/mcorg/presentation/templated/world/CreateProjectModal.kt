package app.mcorg.presentation.templated.world

import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxTargetError
import app.mcorg.presentation.templated.common.form.radiogroup.RadioGroupOption
import app.mcorg.presentation.templated.common.form.radiogroup.radioGroup
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.modal.FormModalHttpMethod
import app.mcorg.presentation.templated.common.modal.FormModalHxValues
import app.mcorg.presentation.templated.common.modal.formModal
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

fun <T : Tag> T.createProjectModal(user: TokenProfile, worldId: Int) = formModal(
    modalId = "create-project-modal",
    title = "Create Project",
    description = "Create a new project for your Minecraft world.",
    saveText = "Create Project",
    hxValues = if (user.isDemoUserInProduction) null else FormModalHxValues(
        hxTarget = "#world-projects-list",
        hxSwap = "afterbegin",
        method = FormModalHttpMethod.POST,
        href = "${Link.Worlds.world(worldId).to}/projects"
    ),
    openButtonBlock = {
        addClass("create-project-button")
        addClass("btn--action")
        iconLeft = Icons.MENU_ADD
        iconSize = IconSize.SMALL
        + "New Project"
    }
) {
    formContent {
        hxTargetError(".validation-error-message")
        if (user.isDemoUserInProduction) {
            onSubmit = "return false;"
        }
        classes += "create-project-form"
        span("input-group") {
            label {
                + "Name"
                span("required-indicator") { +"*" }
            }
            input {
                type = InputType.text
                name = "name"
                minLength = "3"
                maxLength = "100"
                required = true
            }
            p("validation-error-message") {
                id = "validation-error-name"
            }
        }
        span("input-group") {
            label {
                + "Description"
            }
            textArea {
                name = "description"
                maxLength = "500"
            }
            p("validation-error-message") {
                id = "validation-error-description"
            }
        }
        span("input-group") {
            label {
                + "Project Type"
                span("required-indicator") { +"*" }
            }
            div("project-type-select") {
                radioGroup("type", options = ProjectType.entries.map {
                    RadioGroupOption(
                        value = it.name,
                        label = it.toPrettyEnumName()
                    )
                }, selectedOption = "BUILDING")
            }
            p("validation-error-message") {
                id = "validation-error-type"
            }
        }
        if (user.isDemoUserInProduction) {
            p("subtle") {
                + "Project creation is disabled in demo mode."
            }
        }
        // TODO: Dependency selection
    }
}