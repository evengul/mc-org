package app.mcorg.presentation.templated.common.chip

import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.icon.Icon
import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.iconComponent
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.id

fun <T : Tag> T.chipComponent(
    handler: (Chip.() -> Unit)? = null
) {
    val chip = Chip()
    handler?.invoke(chip)
    addComponent(chip)
}

enum class ChipVariant(val iconColor: IconColor) {
    ACTION(IconColor.ON_ACTION),
    NEUTRAL(IconColor.ON_NEUTRAL),
    DANGER(IconColor.ON_DANGER),
    SUCCESS(IconColor.ON_SUCCESS),
    WARNING(IconColor.ON_WARNING),
    INFO(IconColor.ON_INFO),
}

enum class ChipSize {
    SMALL, MEDIUM, LARGE
}

class Chip(
    var text: String = "",
    var icon: Icon? = null,
    var variant: ChipVariant = ChipVariant.ACTION,
    var size: ChipSize = ChipSize.MEDIUM,
    var onClick: String? = null,
    var classes: MutableSet<String> = mutableSetOf(),
    var id: String? = null,
    var hxEditableFromHref: String? = null,
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        container.div {
            this@Chip.id?.let {
                this@div.id = it
            }
            // Apply base chip class and variant modifiers
            this@div.classes = this@Chip.classes + mutableSetOf("chip").apply {
                // Add variant modifier classes following new CSS architecture
                when (this@Chip.variant) {
                    ChipVariant.ACTION -> add("chip--action")
                    ChipVariant.NEUTRAL -> add("chip--neutral")
                    ChipVariant.DANGER -> add("chip--danger")
                    ChipVariant.SUCCESS -> add("chip--success")
                    ChipVariant.WARNING -> add("chip--warning")
                    ChipVariant.INFO -> add("chip--info")
                }

                // Add size modifier classes
                when (this@Chip.size) {
                    ChipSize.SMALL -> add("chip--sm")
                    ChipSize.MEDIUM -> { /* Default size */ }
                    ChipSize.LARGE -> add("chip--lg")
                }

                // Add interactive modifier if clickable
                onClick?.let {
                    add("chip--interactive")
                    add("u-cursor-pointer")
                }

                hxEditableFromHref?.let {
                    add("chip--interactive")
                    add("chip--editable")
                    add("u-cursor-pointer")
                }
            }

            hxEditableFromHref?.let {
                hxGet(it)
                hxSwap("outerHTML")
            }

            onClick?.let {
                attributes["onclick"] = it
            }

            icon?.let {
                iconComponent(it, size = when (this@Chip.size) {
                    ChipSize.SMALL -> IconSize.SMALL
                    ChipSize.MEDIUM -> IconSize.SMALL
                    ChipSize.LARGE -> IconSize.MEDIUM
                }, color = variant.iconColor)
            }

            if (text.isNotEmpty()) {
                + text
            }
        }
    }

    operator fun String.unaryPlus() {
        this@Chip.text = this
    }
}

// Convenience functions for common chip variants
fun <T : Tag> T.actionChip(
    text: String,
    icon: Icon? = null,
    size: ChipSize = ChipSize.MEDIUM,
    onClick: String? = null,
    handler: (Chip.() -> Unit)? = null
) {
    chipComponent {
        this.text = text
        this.icon = icon
        this.variant = ChipVariant.ACTION
        this.size = size
        this.onClick = onClick
        handler?.invoke(this)
    }
}

fun <T : Tag> T.neutralChip(
    text: String,
    icon: Icon? = null,
    size: ChipSize = ChipSize.MEDIUM,
    onClick: String? = null,
    handler: (Chip.() -> Unit)? = null
) {
    chipComponent {
        this.text = text
        this.icon = icon
        this.variant = ChipVariant.NEUTRAL
        this.size = size
        this.onClick = onClick
        handler?.invoke(this)
    }
}

fun <T : Tag> T.infoChip(
    text: String,
    icon: Icon? = null,
    size: ChipSize = ChipSize.MEDIUM,
    onClick: String? = null,
    handler: (Chip.() -> Unit)? = null
) {
    chipComponent {
        this.text = text
        this.icon = icon
        this.variant = ChipVariant.INFO
        this.size = size
        this.onClick = onClick
        handler?.invoke(this)
    }
}