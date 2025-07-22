package app.mcorg.presentation.templated.testpage

import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.button.IconButtonColor
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.neutralButton
import kotlinx.html.MAIN
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.summary

fun MAIN.testButtons() {
    details {
        summary { + "Buttons" }
        div("button-row") {
            actionButton("Primary Button")
            actionButton("Primary Button with Icons") {
                iconLeft = Icons.Dimensions.OVERWORLD
                iconRight = Icons.Dimensions.NETHER
                iconSize = IconSize.SMALL
            }
            neutralButton("Secondary Button")
            neutralButton("Secondary Button with left icon") {
                iconLeft = Icons.Dimensions.END
                iconSize = IconSize.SMALL
            }
            neutralButton("Secondary Button with right icon") {
                iconRight = Icons.Dimensions.OVERWORLD
                iconSize = IconSize.SMALL
            }
            iconButton(Icons.Dimensions.OVERWORLD)
            iconButton(Icons.Dimensions.NETHER, IconSize.SMALL, color = IconButtonColor.DANGER)
            dangerButton("Danger Button")
        }
    }
}