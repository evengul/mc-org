package no.mcorg.templates.pages

import kotlinx.html.ButtonType
import kotlinx.html.button
import kotlinx.html.form
import no.mcorg.clients.Team
import no.mcorg.clients.World

fun createProject(world: World, team: Team): String {
    return page {
        form {
            button {
                type = ButtonType.submit
                + "Create project in ${world.name}/${team.name}"
            }
        }
    }
}