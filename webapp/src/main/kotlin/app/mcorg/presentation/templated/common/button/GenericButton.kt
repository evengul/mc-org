package app.mcorg.presentation.templated.common.button

import app.mcorg.presentation.templated.common.icon.Icon
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.iconComponent
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.icon.IconColor
import kotlinx.html.TagConsumer
import kotlinx.html.button
import kotlinx.html.classes

class GenericButton(
    private val text: String = "",
    var iconLeft: Icon? = null,
    var iconRight: Icon? = null,
    var iconSize: IconSize? = null,
    val onClick: String? = null,
    var classes: Set<String> = setOf("btn"),
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        val iconColor = if (classes.contains("btn-primary")) {
            IconColor.ON_PRIMARY
        } else if (classes.contains("btn-secondary")) {
            IconColor.ON_SECONDARY
        } else if (classes.contains("btn-danger")) {
            IconColor.ON_DANGER
        } else if (classes.contains("icon-primary")) {
            IconColor.ON_PRIMARY
        } else if (classes.contains("icon-secondary")) {
            IconColor.ON_SECONDARY
        } else if (classes.contains("icon-danger")) {
            IconColor.ON_DANGER
        } else if (classes.contains("icon-success")) {
            IconColor.ON_SUCCESS
        } else if (classes.contains("icon-warning")) {
            IconColor.ON_WARNING
        } else if (classes.contains("icon-info")) {
            IconColor.ON_INFO
        } else {
            IconColor.ON_PRIMARY // Default color
        }

        container.button {
            this@button.classes = getAllClasses()
            if (onClick != null) {
                attributes["onclick"] = onClick
            }
            iconLeft?.let {
                iconComponent(it, iconSize ?: IconSize.MEDIUM, iconColor)
            }
            if (text.isNotEmpty()) {
                + text
            }
            iconRight?.let {
                iconComponent(it, iconSize ?: IconSize.MEDIUM, iconColor)
            }
        }
    }

    fun getAllClasses(): Set<String> {
        if (!classes.contains("btn")) {
            classes = classes + "btn"
        }
        if (text.isEmpty() && iconLeft != null) {
            classes = classes + "btn-icon-only"
        } else if (text.isEmpty() && iconRight != null) {
            classes = classes + "btn-icon-only"
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