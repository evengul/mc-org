package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import no.mcorg.domain.*

fun teamPage(world: World, team: Team, projects: List<SlimProject>, packs: List<ResourcePack>): String {
    return page(siteTitle = "${world.name} - ${team.name}") {
        h2 {
            + "Projects"
        }
        button {
            type = ButtonType.button
            + "Create new project in team"
        }
        ul {
            for (project in projects) {
                li {
                    a {
                        href = "/worlds/${world.name}/teams/${team.id}/project/${project.id}"
                        + project.name
                    }
                    button {
                        type = ButtonType.button
                        + "Delete project"
                    }
                }
            }
        }
        h2 {
            + "Resource Packs"
        }
        button {
            type = ButtonType.button
            + "Add resourcepack to team"
        }
        ul {
            for (pack in packs) {
                li {
                    a {
                        href = "/resourcepacks/${pack.id}"
                        + pack.name
                    }
                    button {
                        type = ButtonType.button
                        + "Remove resourcepack from team"
                    }
                }
            }
        }
    }
}