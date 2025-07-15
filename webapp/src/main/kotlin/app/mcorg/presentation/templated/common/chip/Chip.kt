package app.mcorg.presentation.templated.common.chip

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

fun <T : Tag> T.chipComponent(
    handler: (Chip.() -> Unit)? = null
) {
    val chip = Chip()
    handler?.invoke(chip)
    addComponent(chip)
}

enum class ChipColor(val iconColor: IconColor) {
    PRIMARY(IconColor.ON_ACTIVE),
    SECONDARY(IconColor.ON_NEUTRAL),
    DANGER(IconColor.ON_DANGER),
    SUCCESS(IconColor.ON_SUCCESS),
    WARNING(IconColor.ON_WARNING),
    INFO(IconColor.ON_INFO),
}

class Chip(
    var text: String = "",
    var icon: Icon? = null,
    var color: ChipColor = ChipColor.PRIMARY,
    var onClick: String? = null,
    var classes: MutableSet<String> = mutableSetOf(),
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.div {
            this@div.classes = this@Chip.classes + setOf("chip", "chip-${color.name.lowercase()}")
            onClick?.let {
                attributes["onClick"] = it
                this@div.classes += "clickable"
            }
            icon?.let {
                iconComponent(it, size = IconSize.SMALL, color = color.iconColor)
            }
            if (text.isNotEmpty()) {
                + text
            }
        }
    }
}