package no.mcorg.presentation.htmx

import io.ktor.server.application.*
import no.mcorg.presentation.htmx.routing.router

fun Application.configureHtmx() {
    router()
}