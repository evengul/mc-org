package app.mcorg.presentation.templated.dsl

import kotlinx.html.BODY
import kotlinx.html.id
import kotlinx.html.ul

const val ALERT_CONTAINER_ID = "alert-container"

fun BODY.alertContainer() {
    ul {
        id = ALERT_CONTAINER_ID
    }
}
