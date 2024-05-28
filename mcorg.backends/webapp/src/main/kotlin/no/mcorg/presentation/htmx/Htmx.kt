package no.mcorg.presentation.htmx

import io.ktor.server.application.*
import no.mcorg.presentation.htmx.routing.mainRouting

fun Application.configureHtmx() {
    mainRouting()
}