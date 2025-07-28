package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.dangerButton
import kotlinx.html.DIV
import kotlinx.html.InputType
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.textArea

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
            label {
                + "World Name"
            }
            input {
                type = InputType.text
                value = tabData.world.name
            }
            label {
                + "Description"
            }
            textArea {
                + tabData.world.description
            }
            label {
                + "Game Version"
            }
            select {
                MinecraftVersion.supportedVersions.forEach { version ->
                    option {
                        value = version.toString()
                        if (version == tabData.world.version) {
                            selected = true
                        }
                        + "$version"
                    }
                }
            }
            actionButton("Save Changes")
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
            dangerButton("Delete World")
        }
    }
}