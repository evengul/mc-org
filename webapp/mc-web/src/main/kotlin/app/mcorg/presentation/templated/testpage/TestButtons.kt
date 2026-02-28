package app.mcorg.presentation.templated.testpage

import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.button.IconButtonColor
import app.mcorg.presentation.templated.common.button.ButtonSize
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.button.ghostButton
import app.mcorg.presentation.templated.common.button.actionButtonSmall
import app.mcorg.presentation.templated.common.button.actionButtonLarge
import kotlinx.html.MAIN
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.summary

fun MAIN.testButtons() {
    details {
        summary { + "Buttons" }

        // Basic button variants - updated to use new layout classes
        div("flex flex--wrap flex--gap-md u-margin-bottom-md") {
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
            dangerButton("Danger Button")
            ghostButton("Ghost Button")
        }

        // Size variants showcase - new functionality
        h3 { + "Size Variants" }
        div("flex flex--wrap flex--gap-md u-margin-bottom-md") {
            actionButtonSmall("Small Primary")
            actionButton("Medium Primary")
            actionButtonLarge("Large Primary")
        }

        // Icon buttons - enhanced with new variants
        h3 { + "Icon Buttons" }
        div("flex flex--wrap flex--gap-md u-margin-bottom-md") {
            iconButton(Icons.Dimensions.OVERWORLD, "Test button")
            iconButton(Icons.Dimensions.NETHER, "Test button", IconSize.SMALL, color = IconButtonColor.DANGER)
            iconButton(Icons.Dimensions.END, "Test button", color = IconButtonColor.GHOST)
            iconButton(Icons.Dimensions.OVERWORLD, "Test button", size = ButtonSize.SMALL)
            iconButton(Icons.Dimensions.NETHER, "Test Button", size = ButtonSize.LARGE, color = IconButtonColor.DANGER)
        }
    }
}
