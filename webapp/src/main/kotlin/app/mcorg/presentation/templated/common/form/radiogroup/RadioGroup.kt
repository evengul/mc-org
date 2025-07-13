package app.mcorg.presentation.templated.common.form.radiogroup

import app.mcorg.presentation.templated.common.component.LeafComponent
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.input

fun <T : Tag, I> T.radioGroup(
    name: String,
    options: List<I>,
    selectedOption: I? = null,
    radioGroupHandler: (RadioGroup<I>.() -> Unit)? = null
) {
    val element = RadioGroup(name, options, selectedOption)
    radioGroupHandler?.let { it(element) }
    element.render(consumer)
}

class RadioGroup<T>(
    private val inputName: String,
    private val options: List<T>,
    var selectedOption: T? = null
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        options.forEach {
            container.input(type = InputType.radio) {
                attributes["name"] = inputName
                attributes["value"] = it.toString()
                if (it == selectedOption) {
                    attributes["checked"] = "checked"
                }
            }
        }
    }
}