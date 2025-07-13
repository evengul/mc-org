package app.mcorg.presentation.templated.common.button

import app.mcorg.presentation.templated.common.icon.Icon
import app.mcorg.presentation.templated.common.icon.IconSize
import kotlinx.html.Tag

fun <T : Tag> T.primaryButton(text: String, buttonHandler: (GenericButton.() -> Unit)? = null) {
    val button = GenericButton(text = text)
    buttonHandler?.let { it(button) }
    button.addClass("btn-primary")
    button.render(consumer)
}

fun <T : Tag> T.secondaryButton(text: String, buttonHandler: (GenericButton.() -> Unit)? = null) {
    val button = GenericButton(text = text)
    buttonHandler?.let { it(button) }
    button.addClass("btn-secondary")
    button.render(consumer)
}

fun <T : Tag> T.dangerButton(text: String, buttonHandler: (GenericButton.() -> Unit)? = null) {
    val button = GenericButton(text = text)
    buttonHandler?.let { it(button) }
    button.addClass("btn-danger")
    button.render(consumer)
}

enum class IconButtonColor {
    SECONDARY, DANGER
}

fun <T : Tag> T.iconButton(icon: Icon,
                           iconSize: IconSize = IconSize.MEDIUM,
                           color: IconButtonColor = IconButtonColor.SECONDARY) {
    val button = GenericButton(iconLeft = icon, iconSize = iconSize)
    button.addClass("btn-icon")
    when(color) {
        IconButtonColor.SECONDARY -> button.addClass("btn-secondary")
        IconButtonColor.DANGER -> button.addClass("btn-danger")
    }
    button.render(consumer)
}