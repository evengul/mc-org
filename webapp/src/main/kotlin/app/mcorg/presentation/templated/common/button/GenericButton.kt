package app.mcorg.presentation.templated.common.button

import app.mcorg.presentation.templated.common.icon.Icon
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.iconComponent
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.icon.IconColor
import kotlinx.html.BUTTON
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.classes

class GenericButton(
    private var text: String = "",
    var href: String? = null,
    var iconLeft: Icon? = null,
    var iconRight: Icon? = null,
    var iconSize: IconSize? = null,
    var onClick: String? = null,
    var classes: Set<String> = setOf("btn"),
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        href?.let {
            container.a {
                this@a.classes += "link--button"
                this@a.href = it
                button {
                    buttonInternals()
                }
            }
        }

        if (href == null) {
            container.button {
                buttonInternals()
            }
        }


    }

    private fun BUTTON.buttonInternals() {
        val color = getColor()
        this.classes = getAllClasses()
        onClick?.let {
            attributes["onclick"] = it
        }
        iconLeft?.let {
            iconComponent(it, iconSize ?: IconSize.MEDIUM, color)
        }
        if (text.isNotEmpty()) {
            + text
        }
        iconRight?.let {
            iconComponent(it, iconSize ?: IconSize.MEDIUM, color)
        }
    }

    private fun getColor(): IconColor {
        return if (classes.contains("btn--action")) {
            IconColor.ON_ACTION
        } else if (classes.contains("btn--neutral")) {
            IconColor.ON_NEUTRAL
        } else if (classes.contains("btn--danger")) {
            IconColor.ON_DANGER
        } else if (classes.contains("btn--ghost")) {
            IconColor.ON_BACKGROUND
        } else if (classes.contains("icon--action")) {
            IconColor.ON_ACTION
        }  else if (classes.contains("icon--neutral")) {
            IconColor.ON_NEUTRAL
        } else if (classes.contains("icon--danger")) {
            IconColor.ON_DANGER
        } else if (classes.contains("icon--success")) {
            IconColor.ON_SUCCESS
        } else if (classes.contains("icon--warning")) {
            IconColor.ON_WARNING
        } else if (classes.contains("icon--info")) {
            IconColor.ON_INFO
        } else  {
            IconColor.ON_ACTION // Default color
        }
    }

    operator fun String.unaryPlus() {
        text = this
    }

    fun getAllClasses(): Set<String> {
        if (!classes.contains("btn")) {
            classes = classes + "btn"
        }
        if (text.isEmpty() && iconLeft != null) {
            classes = classes + "btn--icon-only"
        } else if (text.isEmpty() && iconRight != null) {
            classes = classes + "btn--icon-only"
        }
        return if (classes.contains("btn")) {
            classes
        } else {
            classes + "btn"
        }
    }

    fun addClass(className: String): GenericButton {
        classes += className
        return this
    }
}