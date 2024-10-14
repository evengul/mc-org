package app.mcorg.presentation.utils

import io.ktor.server.application.*

fun ApplicationCall.getCurrentUrl() = request.headers["HX-Current-URL"]