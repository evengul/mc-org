package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent

@Suppress("UNUSED_PARAMETER")
fun FlowContent.emptyState(heading: String, body: String? = null, block: (FlowContent.() -> Unit)? = null) {
}

@Suppress("UNUSED_PARAMETER")
fun FlowContent.emptyStateCards(block: FlowContent.() -> Unit) {
}
