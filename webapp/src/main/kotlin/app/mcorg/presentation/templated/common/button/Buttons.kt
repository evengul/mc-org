package app.mcorg.presentation.templated.common.button

import app.mcorg.presentation.templated.common.icon.Icon
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import kotlinx.html.Tag

fun <T : Tag> T.actionButton(text: String, buttonHandler: (GenericButton.() -> Unit)? = null) {
    val button = GenericButton(text = text)
    buttonHandler?.let { it(button) }
    button.addClass("btn--action")
    button.render(consumer)
}

fun <T : Tag> T.neutralButton(text: String, buttonHandler: (GenericButton.() -> Unit)? = null) {
    val button = GenericButton(text = text)
    buttonHandler?.let { it(button) }
    button.addClass("btn--neutral")
    button.render(consumer)
}

fun <T : Tag> T.dangerButton(text: String, buttonHandler: (GenericButton.() -> Unit)? = null) {
    val button = GenericButton(text = text)
    buttonHandler?.let { it(button) }
    button.addClass("btn--danger")
    button.render(consumer)
}

fun <T : Tag> T.ghostButton(text: String, buttonHandler: (GenericButton.() -> Unit)? = null) {
    val button = GenericButton(text = text)
    buttonHandler?.let { it(button) }
    button.addClass("btn--ghost")
    button.render(consumer)
}

fun <T : Tag> T.backButton(text: String, link: Link) = ghostButton(text) {
    href = link.to
    iconLeft = Icons.BACK
    iconSize = IconSize.SMALL
    id = "button-back"
    addClass("btn--back")
    addClass("btn--sm")
}

enum class IconButtonColor {
    NEUTRAL, DANGER, GHOST
}

enum class ButtonSize {
    SMALL, MEDIUM, LARGE
}

fun <T : Tag> T.iconButton(
    icon: Icon,
    ariaLabel: String,
    iconSize: IconSize = IconSize.MEDIUM,
    color: IconButtonColor = IconButtonColor.NEUTRAL,
    size: ButtonSize = ButtonSize.MEDIUM,
    buttonHandler: (GenericButton.() -> Unit)? = null
) {
    val button = GenericButton(iconLeft = icon, iconSize = iconSize, ariaLabel = ariaLabel)
    // Updated to use new CSS methodology
    button.addClass("btn--icon-only")

    // Apply color variant
    when(color) {
        IconButtonColor.NEUTRAL -> button.addClass("btn--neutral")
        IconButtonColor.DANGER -> button.addClass("btn--danger")
        IconButtonColor.GHOST -> button.addClass("btn--ghost")
    }

    // Apply size variant
    when(size) {
        ButtonSize.SMALL -> button.addClass("btn--sm")
        ButtonSize.MEDIUM -> { /* Default size, no additional class needed */ }
        ButtonSize.LARGE -> button.addClass("btn--lg")
    }

    buttonHandler?.let { it(button) }
    button.render(consumer)
}

// Enhanced button functions with size variants
fun <T : Tag> T.actionButtonSmall(text: String, buttonHandler: (GenericButton.() -> Unit)? = null) {
    val button = GenericButton(text = text)
    buttonHandler?.let { it(button) }
    button.addClass("btn--action")
    button.addClass("btn--sm")
    button.render(consumer)
}

fun <T : Tag> T.actionButtonLarge(text: String, buttonHandler: (GenericButton.() -> Unit)? = null) {
    val button = GenericButton(text = text)
    buttonHandler?.let { it(button) }
    button.addClass("btn--action")
    button.addClass("btn--lg")
    button.render(consumer)
}
