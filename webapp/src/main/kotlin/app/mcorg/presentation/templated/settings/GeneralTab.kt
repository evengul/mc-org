package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.hxConfirm
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxPut
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.link.Link
import kotlinx.html.DIV
import kotlinx.html.FORM
import kotlinx.html.FormEncType
import kotlinx.html.InputType
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.textArea

fun FORM.worldSettingsForm(world: World) {
    id = "world-general-settings-form"
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#world-general-settings-form")
    hxPut("/app/worlds/${world.id}")

    label {
        htmlFor = "world-settings-name-input"
        + "World Name"
    }
    input {
        name = "name"
        id = "world-settings-name-input"
        type = InputType.text
        value = world.name
    }
    label {
        htmlFor = "world-settings-description-input"
        + "Description"
    }
    textArea {
        id = "world-settings-description-input"
        name = "description"
        + world.description
    }
    label {
        htmlFor = "world-settings-version-select"
        + "Game Version"
    }
    select {
        id = "world-settings-version-select"
        name = "version"
        MinecraftVersion.supportedVersions.forEach { version ->
            option {
                value = version.toString()
                selected = version == world.version
                + "$version"
            }
        }
    }
    actionButton("Save Changes")
}

fun FORM.worldNameForm(world: World) {
    id = "world-name-form"
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#world-name-form")
    hxPatch("/app/worlds/${world.id}/settings/name")

    label {
        htmlFor = "world-name-input"
        + "World Name"
    }
    input {
        name = "name"
        id = "world-name-input"
        type = InputType.text
        value = world.name
        classes += "form-control"
    }
    actionButton("Update Name")
}

fun FORM.worldDescriptionForm(world: World) {
    id = "world-description-form"
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#world-description-form")
    hxPatch("/app/worlds/${world.id}/settings/description")

    label {
        htmlFor = "world-description-input"
        + "World Description"
    }
    textArea {
        name = "description"
        id = "world-description-input"
        classes += "form-control"
        + world.description
    }
    actionButton("Update Description")
}

fun FORM.worldVersionForm(world: World) {
    id = "world-version-form"
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#world-version-form")
    hxPatch("/app/worlds/${world.id}/settings/version")

    label {
        htmlFor = "world-version-select"
        + "Game Version"
    }
    select {
        name = "version"
        id = "world-version-select"
        classes += "form-control"
        MinecraftVersion.supportedVersions.forEach { version ->
            option {
                value = version.toString()
                selected = version == world.version
                + "$version"
            }
        }
    }
    actionButton("Update Version")
}

fun DIV.generalTab(tabData: SettingsTab.General) {
    classes += "settings-general-tab"
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
            worldVersionForm(tabData.world)
        }
    }

    div("danger-zone") {
        div("danger-zone-header") {
            h3 {
                + "Danger Zone"
            }
            p("subtle") {
                + "Actions in this section can lead to permanent data loss"
            }
        }
        div("danger-zone-content") {
            p {
                + "Delete this world"
            }
            p("subtle") {
                + "Once you delete a world, there is no going back. All projects, tasks, and resources will be permanently deleted."
            }
            dangerButton("Delete World") {
                buttonBlock = {
                    hxDelete(Link.Worlds.world(tabData.world.id).settings().to)
                    hxConfirm("Are you sure you want to delete this world? This action cannot be undone.")
                }
            }
        }
    }
}