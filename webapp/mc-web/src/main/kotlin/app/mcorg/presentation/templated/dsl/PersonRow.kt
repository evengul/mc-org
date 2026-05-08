package app.mcorg.presentation.templated.dsl

import kotlinx.html.DIV
import kotlinx.html.LI
import kotlinx.html.UL
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.li

class PersonRowScope(val li: LI) {
    private var startBlock: (DIV.() -> Unit)? = null
    private var endBlock: (DIV.() -> Unit)? = null

    fun start(block: DIV.() -> Unit) {
        startBlock = block
    }

    fun end(block: DIV.() -> Unit) {
        endBlock = block
    }

    fun render() {
        with(li) {
            div("person-row__start") {
                startBlock?.invoke(this)
            }
            endBlock?.let { actions ->
                div("person-row__end") {
                    actions.invoke(this)
                }
            }
        }
    }
}

fun UL.personRow(
    rowId: String? = null,
    empty: Boolean = false,
    block: PersonRowScope.() -> Unit,
) {
    li {
        classes = setOf("person-row") + if (empty) setOf("person-row--empty") else emptySet()
        rowId?.let { this.id = it }
        PersonRowScope(this).apply(block).render()
    }
}
