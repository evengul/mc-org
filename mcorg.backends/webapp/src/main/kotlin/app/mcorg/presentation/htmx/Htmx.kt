package app.mcorg.presentation.htmx

import io.ktor.server.application.*
import app.mcorg.presentation.htmx.routing.router

fun Application.configureHtmx() {
    router()
}