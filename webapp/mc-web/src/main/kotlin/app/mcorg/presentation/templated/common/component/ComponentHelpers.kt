package app.mcorg.presentation.templated.common.component

import kotlinx.html.Tag

fun <T : Tag> T.addComponent(component: Component) {
    component.render(consumer)
}