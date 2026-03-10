package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div

fun FlowContent.planExecuteToggle(active: String) {
    div("toggle") {
        button {
            classes = buildSet {
                add("toggle__btn")
                if (active == "PLAN") add("toggle__btn--active")
            }
            +"PLAN"
        }
        button {
            classes = buildSet {
                add("toggle__btn")
                if (active == "EXECUTE") add("toggle__btn--active")
            }
            +"EXEC"
        }
    }
}
