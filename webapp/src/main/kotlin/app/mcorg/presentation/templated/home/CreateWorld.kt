package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxTargetError
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.modal.FormModalHttpMethod
import app.mcorg.presentation.templated.common.modal.FormModalHxValues
import app.mcorg.presentation.templated.common.modal.formModal
import kotlinx.html.*

fun <T : Tag> T.createWorldModal(user: TokenProfile, versions: List<MinecraftVersion.Release>) = formModal(
    modalId = "create-world-modal",
    title = "Create World",
    description = "Create a new Minecraft world to organize your projects.",
    saveText = "Create World",
    hxValues = if (user.isDemoUserInProduction) null else FormModalHxValues(
        hxTarget = "#home-worlds",
        method = FormModalHttpMethod.POST,
        href = "/app/worlds"
    ),
    openButtonBlock = {
        addClass("create-world-button")
        addClass("btn--action")
        iconLeft = Icons.ADD_WORLD
        iconSize = IconSize.SMALL
        + "Create World"
    }
) {
    formContent {
        hxTargetError(".validation-error-message")
        if (user.isDemoUserInProduction) {
            onSubmit = "return false;"
        }
        label {
            htmlFor = "create-world-name"
            + "World Name"
            span("required-indicator") { +"*" }
        }
        input {
            id = "create-world-name"
            type = InputType.text
            name = "name"
            placeholder = "My survival world"
            maxLength = "100"
            minLength = "3"
            required = true
        }
        p("validation-error-message") {
            id = "validation-error-name"
        }

        label {
            htmlFor = "create-world-description"
            + "Description"
        }
        textArea {
            id = "create-world-description"
            name = "description"
            maxLength = "500"
            placeholder = "A brief description of the world"
        }
        p("validation-error-message") {
            id = "validation-error-description"
        }

        label {
            htmlFor = "create-world-version"
            + "Minecraft Version"
            span("required-indicator") { +"*" }
        }
        select {
            id = "create-world-version"
            name = "version"
            required = true
            versions.forEachIndexed { i, version ->
                option {
                    value = version.toString()
                    + "$version ${if(i == 0) " (Latest)" else if (i == versions.size -1) "(Earliest compatible)" else ""}"
                }
            }
        }
        p("validation-error-message") {
            id = "validation-error-version"
        }
        if (user.isDemoUserInProduction) {
            p("subtle") {
                + "Demo users cannot create worlds. Please register for a full account to access this feature."
            }
        }
    }
}