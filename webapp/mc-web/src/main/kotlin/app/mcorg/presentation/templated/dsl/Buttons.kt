package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.classes

fun FlowContent.primaryButton(small: Boolean = false, block: FlowContent.() -> Unit) {
    button {
        classes = buildSet {
            add("btn")
            add("btn--primary")
            if (small) add("btn--sm")
        }
        block()
    }
}

fun FlowContent.secondaryButton(small: Boolean = false, block: FlowContent.() -> Unit) {
    button {
        classes = buildSet {
            add("btn")
            add("btn--secondary")
            if (small) add("btn--sm")
        }
        block()
    }
}

fun FlowContent.ghostButton(small: Boolean = false, block: FlowContent.() -> Unit) {
    button {
        classes = buildSet {
            add("btn")
            add("btn--ghost")
            if (small) add("btn--sm")
        }
        block()
    }
}

fun FlowContent.dangerButton(small: Boolean = false, block: FlowContent.() -> Unit) {
    button {
        classes = buildSet {
            add("btn")
            add("btn--danger")
            if (small) add("btn--sm")
        }
        block()
    }
}
