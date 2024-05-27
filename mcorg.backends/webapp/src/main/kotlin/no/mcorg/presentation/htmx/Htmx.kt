package no.mcorg.presentation.htmx

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import no.mcorg.presentation.htmx.auth.configureAuth

fun Application.configureHtmx() {
    configureAuth()
}