package app.mcorg.presentation.templated.common.form.radiogroup

import app.mcorg.presentation.templated.common.component.LeafComponent
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.div
import kotlinx.html.classes

data class RadioGroupOption(
    val value: String,
    val label: String
)

enum class RadioGroupSize {
    SMALL, MEDIUM, LARGE
}

enum class RadioGroupState {
    DEFAULT, ERROR, SUCCESS
}

enum class RadioGroupLayout {
    VERTICAL, HORIZONTAL
}

fun <T : Tag> T.radioGroup(
    name: String,
    options: List<RadioGroupOption>,
    selectedOption: String? = null,
    size: RadioGroupSize = RadioGroupSize.MEDIUM,
    state: RadioGroupState = RadioGroupState.DEFAULT,
    layout: RadioGroupLayout = RadioGroupLayout.VERTICAL,
    radioGroupHandler: (RadioGroup.() -> Unit)? = null
) {
    val element = RadioGroup(name, options, selectedOption, size, state, layout)
    radioGroupHandler?.let { it(element) }
    element.render(consumer)
}

class RadioGroup(
    private val inputName: String,
    private val options: List<RadioGroupOption>,
    var selectedOption: String? = null,
    var size: RadioGroupSize = RadioGroupSize.MEDIUM,
    var state: RadioGroupState = RadioGroupState.DEFAULT,
    var layout: RadioGroupLayout = RadioGroupLayout.VERTICAL
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        container.div {
            // Apply layout utility classes
            classes = when (layout) {
                RadioGroupLayout.VERTICAL -> setOf("stack", "stack--sm")
                RadioGroupLayout.HORIZONTAL -> setOf("cluster", "cluster--sm")
            }

            options.forEach { option ->
                div {
                    classes = setOf("u-flex", "u-flex-gap-xs")

                    input(type = InputType.radio) {
                        id = "${inputName}-${option.value}"
                        attributes["name"] = inputName
                        attributes["value"] = option.value

                        // Apply form control base class and variants
                        classes = mutableSetOf("form-control")

                        // Add size variants
                        when (this@RadioGroup.size) {
                            RadioGroupSize.SMALL -> classes = classes + "radio--sm"
                            RadioGroupSize.MEDIUM -> { /* Default size */ }
                            RadioGroupSize.LARGE -> classes = classes + "radio--lg"
                        }

                        // Add state variants
                        when (this@RadioGroup.state) {
                            RadioGroupState.ERROR -> classes = classes + "input--error"
                            RadioGroupState.SUCCESS -> classes = classes + "input--success"
                            RadioGroupState.DEFAULT -> { /* Default state */ }
                        }

                        if (option.value.lowercase() == selectedOption?.lowercase()) {
                            attributes["checked"] = "checked"
                        }
                    }

                    label {
                        htmlFor = "${inputName}-${option.value}"
                        classes = setOf("u-cursor-pointer")
                        + option.label
                    }
                }
            }
        }
    }
}