package app.mcorg.presentation.templated.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.presentation.templated.common.form.radiogroup.RadioGroupOption
import app.mcorg.presentation.templated.common.form.radiogroup.radioGroup
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.modal.FormModalHttpMethod
import app.mcorg.presentation.templated.common.modal.FormModalHxValues
import app.mcorg.presentation.templated.common.modal.formModal
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.textArea

fun <T : Tag> T.createProjectModal(worldId: Int, supportedVersions: List<MinecraftVersion.Release>) = formModal(
    modalId = "create-project-modal",
    title = "Create Project",
    description = "Create a new project for your Minecraft world.",
    saveText = "Create Project",
    hxValues = FormModalHxValues(
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
        classes += "create-project-form"
        span("input-group") {
            label {
                + "Name"
            }
            input {
                type = InputType.text
                name = "name"
                required = true
            }
        }
        span("input-group") {
            label {
                + "Description"
            }
            textArea {
                name = "description"
            }
        }
        span("input-group") {
            label {
                + "Project Type"
            }
            div("project-type-select") {
                radioGroup("type", options = ProjectType.entries.map {
                    RadioGroupOption(
                        value = it.name,
                        label = it.toPrettyEnumName()
                    )
                }, selectedOption = "BUILDING")
            }
        }
        span("input-group") {
            label {
                + "Minecraft Version"
            }
            select {
                name = "version"
                required = true
                supportedVersions.forEach {
                    option {
                        value = it.toString()
                        + it.toString()
                    }
                }
            }
        }
        // TODO: Dependency selection
    }
}