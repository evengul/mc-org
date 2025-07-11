package app.mcorg.presentation.templated.common.component

import kotlinx.html.TagConsumer

interface Component {
    fun render(container: TagConsumer<*>)
}