package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent

@Suppress("UNUSED_PARAMETER")
fun FlowContent.taskList(block: FlowContent.() -> Unit) {
}

@Suppress("UNUSED_PARAMETER")
fun FlowContent.taskRow(name: String, done: Boolean = false) {
}
