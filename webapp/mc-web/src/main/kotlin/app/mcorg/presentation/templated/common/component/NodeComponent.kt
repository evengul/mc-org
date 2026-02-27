package app.mcorg.presentation.templated.common.component

import kotlinx.html.TagConsumer

abstract class NodeComponent : Component {
    private val children = mutableListOf<Component>()

    fun addComponent(component: Component): NodeComponent {
        children.add(component)
        return this
    }

    fun renderChildren(container: TagConsumer<*>) {
        children.forEach { it.render(container) }
    }
}