package no.mcorg.presentation.templates.pages

import kotlinx.html.*
import no.mcorg.presentation.clients.Team
import no.mcorg.presentation.clients.World
import no.mcorg.presentation.clients.getProjectsInTeam
import no.mcorg.presentation.clients.getTeams

fun landingPage(worlds: List<World>): String {

    if(worlds.isEmpty()) {
        return firstContact()
    }

    if (worlds.size == 1) {
        return teamsPage(worlds.first())
    }

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

fun teamsPage(world: World): String {
    val teams = getTeams()

    if(teams.isEmpty()) {
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

    if (teams.size == 1) {
        return projectsPage(world, teams.first())
    }

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

fun projectsPage(world: World, team: Team): String {
    val projects = getProjectsInTeam(team.id)

    if(projects.isEmpty()) {
        return createProject(world, team)
    }

    if (projects.size == 1) {
        return projectPage(projects.first())
    }

    return page (title = "${world.name}/${team.name}: Projects") {
        ul {
            for (project in projects) {
                li {
                    + project.name
                }
            }
        }
    }
}