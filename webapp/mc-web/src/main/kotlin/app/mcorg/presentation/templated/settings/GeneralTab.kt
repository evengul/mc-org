package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.*
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.dangerzone.dangerZone
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.layout.alert.ALERT_CONTAINER_ID
import kotlinx.html.*

fun FORM.worldNameForm(world: World) {
    id = "world-name-form"
    hxTargetError(".validation-error-message")
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#$ALERT_CONTAINER_ID")
    hxSwap("afterbegin")
    hxPatch("/app/worlds/${world.id}/settings/name")
    hxTrigger("input changed delay:500ms from:#world-name-input, submit")

    label {
        htmlFor = "world-name-input"
        + "World Name"
        span("required-indicator") { +"*" }
    }
    input {
        name = "name"
        id = "world-name-input"
        type = InputType.text
        value = world.name
        required = true
        minLength = "3"
        maxLength = "100"
        classes += "form-control"
    }
    p("validation-error-message") {
        id = "validation-error-name"
    }
}

fun FORM.worldDescriptionForm(world: World) {
    id = "world-description-form"
    hxTargetError(".validation-error-message")
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#$ALERT_CONTAINER_ID")
    hxSwap("afterbegin")
    hxPatch("/app/worlds/${world.id}/settings/description")
    hxTrigger("input changed delay:500ms from:#world-description-input, submit")

    label {
        htmlFor = "world-description-input"
        + "World Description"
    }
    textArea {
        name = "description"
        id = "world-description-input"
        classes += "form-control"
        maxLength = "500"
        + world.description
    }
    p("validation-error-message") {
        id = "validation-error-description"
    }
}

fun FORM.worldVersionForm(world: World, supportedVersions: List<MinecraftVersion.Release>) {
    id = "world-version-form"
    hxTargetError(".validation-error-message")
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#$ALERT_CONTAINER_ID")
    hxSwap("afterbegin")
    hxPatch("/app/worlds/${world.id}/settings/version")
    hxTrigger("change changed delay:500ms from:#world-version-select")

    label {
        htmlFor = "world-version-select"
        + "Game Version"
        span("required-indicator") { +"*" }
    }
    select {
        name = "version"
        id = "world-version-select"
        classes += "form-control"
        required = true
        supportedVersions.forEach { version ->
            option {
                value = version.toString()
                selected = version == world.version
                + "$version"
            }
        }
    }
    p("validation-error-message") {
        id = "validation-error-version"
    }
}

fun DIV.generalTab(tabData: SettingsTab.General) {
    classes += "settings-general-tab world-settings-content"
    div("general-settings") {
        div("general-settings-header") {
            h2 {
                + "General Settings"
            }
            p("subtle") {
                + "Configure basic settings for your world"
            }
        }
        form {
            worldNameForm(tabData.world)
        }
        form {
            worldDescriptionForm(tabData.world)
        }
        form {
            worldVersionForm(tabData.world, tabData.supportedVersions)
        }
    }

    dangerZone(description = "Once you delete a world, there is no going back. All projects, tasks, and resources will be permanently deleted.") {
        dangerButton("Delete World") {
            buttonBlock = {
                hxDeleteWithConfirm(
                    url = Link.Worlds.world(tabData.world.id).settings().to,
                    title = "Delete World",
                    description = "This action cannot be undone. All projects, tasks, and resources will be permanently deleted.",
                    warning = " Warning: This will permanently delete the world \"${tabData.world.name}\" and all associated data.",
                    confirmText = tabData.world.name,
                )
            }
        }
    }
}