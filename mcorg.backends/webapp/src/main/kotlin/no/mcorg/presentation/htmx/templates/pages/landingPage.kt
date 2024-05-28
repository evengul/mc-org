package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import no.mcorg.domain.SlimProject
import no.mcorg.domain.Team
import no.mcorg.domain.World

fun worldsPage(worlds: List<World>): String {
    return page(title = "Your worlds") {
        ul {
            for(world in worlds) {
                li {
                    + world.name
                }
            }
        }
    }
}

fun noTeamsPage(): String {
    return page {
        h2 {
            + "Your world has no teams. This should never happen, but will you please create a team for us?"
        }
        form {
            firstWorldTeam()
            button {
                type = ButtonType.submit
                + "Create team"
            }
        }
    }
}

fun teamsPage(world: World, teams: List<Team>): String {
    return page (title = world.name + ": Teams") {
        ul {
            for (team in teams) {
                li {
                    + team.name
                }
            }
        }
    }
}

fun projectsPage(world: World, team: Team, projectsInTeam: List<SlimProject>): String {
    return page (title = "${world.name}/${team.name}: Projects") {
        ul {
            for (project in projectsInTeam) {
                li {
                    + project.name
                }
            }
        }
    }
}