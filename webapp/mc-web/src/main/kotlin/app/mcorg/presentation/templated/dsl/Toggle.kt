package app.mcorg.presentation.templated.dsl

import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div

fun FlowContent.planExecuteToggle(worldId: Int, active: String) {
    div("toggle") {
        button {
            classes = buildSet {
                add("toggle__btn")
                if (active == "plan") add("toggle__btn--active")
            }
            hxGet("/worlds/$worldId/projects/list-fragment?view=plan")
            hxTarget("#projects-view")
            hxSwap("outerHTML")
            attributes["hx-push-url"] = "/worlds/$worldId/projects?view=plan"
            +"PLAN"
        }
        button {
            classes = buildSet {
                add("toggle__btn")
                if (active == "execute") add("toggle__btn--active")
            }
            hxGet("/worlds/$worldId/projects/list-fragment?view=execute")
            hxTarget("#projects-view")
            hxSwap("outerHTML")
            attributes["hx-push-url"] = "/worlds/$worldId/projects?view=execute"
            +"EXEC"
        }
    }
}
