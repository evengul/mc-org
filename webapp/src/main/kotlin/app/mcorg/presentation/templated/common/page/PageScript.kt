package app.mcorg.presentation.templated.common.page

import app.mcorg.presentation.templated.common.page.PageScript.DIALOGS
import app.mcorg.presentation.templated.common.page.PageScript.DRAGGABLE
import app.mcorg.presentation.templated.common.page.PageScript.HTMX
import app.mcorg.presentation.templated.common.page.PageScript.REMOVE_FIRST_PROJECT_DIALOG_ON_CREATE
import app.mcorg.presentation.templated.common.page.PageScript.RESPONSE_TARGETS
import app.mcorg.presentation.templated.common.page.PageScript.TOGGLE_VISIBILITY
import io.ktor.util.generateNonce
import kotlinx.html.HEAD
import kotlinx.html.script

enum class PageScript {
    HTMX,
    RESPONSE_TARGETS,
    DIALOGS,
    DRAGGABLE,
    TOGGLE_VISIBILITY,
    REMOVE_FIRST_PROJECT_DIALOG_ON_CREATE;
}

fun HEAD.addScript(script: PageScript) {
    when (script) {
        HTMX -> script { src = "/static/scripts/htmx.js"; nonce = generateNonce() }
        RESPONSE_TARGETS -> script { src = "/static/scripts/response-targets.js" }
        DIALOGS -> script { src = "/static/scripts/dialogs.js"; nonce = generateNonce() }
        DRAGGABLE -> script { src = "/static/scripts/draggable.js"; nonce = generateNonce() }
        TOGGLE_VISIBILITY -> script { src = "/static/scripts/toggleVisibility.js"; nonce = generateNonce() }
        REMOVE_FIRST_PROJECT_DIALOG_ON_CREATE -> script { src = "/static/scripts/remove-first-project-dialog-on-create.js"; nonce = generateNonce() }
    }
}