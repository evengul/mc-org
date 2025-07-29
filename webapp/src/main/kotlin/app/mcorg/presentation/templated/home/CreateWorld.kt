package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.modal.FormModalHxValues
import app.mcorg.presentation.templated.common.modal.FormModalHttpMethod
import app.mcorg.presentation.templated.common.modal.formModal
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.textArea

fun <T : Tag> T.createWorldModal(versions: List<MinecraftVersion.Release>) = formModal(
    modalId = "create-world-modal",
    title = "Create World",
    description = "Create a new Minecraft world to organize your projects.",
    saveText = "Create World",
    hxValues = FormModalHxValues(
        hxTarget = "#home-worlds-list",
        method = FormModalHttpMethod.POST,
        href = "/app/worlds"
    ),
    openButtonBlock = {
        addClass("create-world-button")
        addClass("btn-action")
        iconLeft = Icons.ADD_WORLD
        iconSize = IconSize.SMALL
        + "Create World"
    }
) {
    formContent {
        label {
            htmlFor = "create-world-name"
            + "World Name"
        }
        input {
            id = "create-world-name"
            type = InputType.text
            name = "name"
            placeholder = "My survival world"
        }

        label {
            htmlFor = "create-world-description"
            + "Description"
        }
        textArea {
            id = "create-world-description"
            name = "description"
            placeholder = "A brief description of the world"
        }

        label {
            htmlFor = "create-world-version"
            + "Minecraft Version"
        }
        select {
            id = "create-world-version"
            name = "version"
            versions.forEachIndexed { i, version ->
                option {
                    value = version.toString()
                    + "$version ${if(i == 0) " (Latest)" else ""}"
                }
            }
        }
    }
}