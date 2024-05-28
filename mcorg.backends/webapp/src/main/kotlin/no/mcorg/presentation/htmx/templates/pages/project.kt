package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.h2
import no.mcorg.domain.Project

fun projectPage(project: Project): String {
    return page(title = project.name) {
        h2 {
            + "This is a project you have created"
        }
    }
}