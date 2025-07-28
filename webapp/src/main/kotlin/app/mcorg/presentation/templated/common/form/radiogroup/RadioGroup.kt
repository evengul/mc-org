package app.mcorg.presentation.templated.common.form.radiogroup

import app.mcorg.presentation.templated.common.component.LeafComponent
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label

data class RadioGroupOption(
    val value: String,
    val label: String
)

fun <T : Tag> T.radioGroup(
    name: String,
    options: List<RadioGroupOption>,
    selectedOption: String? = null,
    radioGroupHandler: (RadioGroup.() -> Unit)? = null
) {
    val element = RadioGroup(name, options, selectedOption)
    radioGroupHandler?.let { it(element) }
    element.render(consumer)
}

class RadioGroup(
    private val inputName: String,
    private val options: List<RadioGroupOption>,
    var selectedOption: String? = null
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        options.forEach {
            container.label {
                htmlFor = "${inputName}-${it.value}"
                + it.label
            }
            container.input(type = InputType.radio) {
                id = "${inputName}-${it.value}"
                attributes["name"] = inputName
                attributes["value"] = it.value
                if (it.value.lowercase() == selectedOption?.lowercase()) {
                    attributes["checked"] = "checked"
                }
            }
        }
    }
}