package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.classes

fun FlowContent.primaryButton(small: Boolean = false, block: FlowContent.() -> Unit) {
    button {
        classes = buildSet {
            add("btn")
            add("btn--primary")
            if (small) add("btn--sm")
        }
        block()
    }
}

fun FlowContent.secondaryButton(small: Boolean = false, block: FlowContent.() -> Unit) {
    button {
        classes = buildSet {
            add("btn")
            add("btn--secondary")
            if (small) add("btn--sm")
        }
        block()
    }
}

fun FlowContent.ghostButton(small: Boolean = false, block: FlowContent.() -> Unit) {
    button {
        classes = buildSet {
            add("btn")
            add("btn--ghost")
            if (small) add("btn--sm")
        }
        block()
    }
}

fun FlowContent.dangerButton(small: Boolean = false, block: FlowContent.() -> Unit) {
    button {
        classes = buildSet {
            add("btn")
            add("btn--danger")
            if (small) add("btn--sm")
        }
        block()
    }
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
    button.addClass("btn--icon-only")

    when (color) {
        IconButtonColor.NEUTRAL -> button.addClass("btn--neutral")
        IconButtonColor.DANGER -> button.addClass("btn--danger")
        IconButtonColor.GHOST -> button.addClass("btn--ghost")
    }

    when (size) {
        ButtonSize.SMALL -> button.addClass("btn--sm")
        ButtonSize.MEDIUM -> { /* default */ }
        ButtonSize.LARGE -> button.addClass("btn--lg")
    }

    buttonHandler?.let { it(button) }
    button.render(consumer)
}
