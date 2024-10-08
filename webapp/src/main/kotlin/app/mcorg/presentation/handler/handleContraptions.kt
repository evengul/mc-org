package app.mcorg.presentation.handler

import app.mcorg.presentation.utils.getContraptionId
import io.ktor.server.application.*

fun ApplicationCall.handleGetContraptions() {

}

fun ApplicationCall.handleGetContraption() {
    val contraptionId = getContraptionId()
}