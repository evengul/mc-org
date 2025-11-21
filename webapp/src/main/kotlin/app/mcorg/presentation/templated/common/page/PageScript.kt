package app.mcorg.presentation.templated.common.page

import app.mcorg.presentation.templated.common.page.PageScript.*
import io.ktor.util.*
import kotlinx.html.HEAD
import kotlinx.html.ScriptCrossorigin
import kotlinx.html.script

enum class PageScript {
    THEME_SWITCHER,
    HTMX,
    RESPONSE_TARGETS,
    DIALOGS,
    DRAGGABLE,
    TOGGLE_VISIBILITY,
    REMOVE_FIRST_PROJECT_DIALOG_ON_CREATE,
    CONFIRMATION_MODAL,
    SEARCHABLE_SELECT,
    IDEA_ITEM_REQUIREMENTS;
}

fun HEAD.addScript(script: PageScript) {
    when (script) {
        THEME_SWITCHER -> script { src = "/static/scripts/theme-switcher.js"; nonce = generateNonce() }
        HTMX -> script { src = "https://cdn.jsdelivr.net/npm/htmx.org@2.0.8/dist/htmx.min.js"; integrity = "sha384-/TgkGk7p307TH7EXJDuUlgG3Ce1UVolAOFopFekQkkXihi5u/6OCvVKyz1W+idaz"; crossorigin = ScriptCrossorigin.anonymous }
        RESPONSE_TARGETS -> script { src = "/static/scripts/response-targets.js" }
        DIALOGS -> script { src = "/static/scripts/dialogs.js"; nonce = generateNonce() }
        DRAGGABLE -> script { src = "/static/scripts/draggable.js"; nonce = generateNonce() }
        TOGGLE_VISIBILITY -> script { src = "/static/scripts/toggleVisibility.js"; nonce = generateNonce() }
        REMOVE_FIRST_PROJECT_DIALOG_ON_CREATE -> script { src = "/static/scripts/remove-first-project-dialog-on-create.js"; nonce = generateNonce() }
        CONFIRMATION_MODAL -> script { src = "/static/scripts/confirmation-modal.js"; nonce = generateNonce() }
        SEARCHABLE_SELECT -> script { src = "/static/scripts/searchable-select.js"; nonce = generateNonce() }
        IDEA_ITEM_REQUIREMENTS -> script { src = "/static/scripts/ideaItemRequirements.js"; nonce = generateNonce() }
    }
}

