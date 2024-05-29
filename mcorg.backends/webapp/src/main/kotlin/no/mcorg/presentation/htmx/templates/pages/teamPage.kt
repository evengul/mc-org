package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import no.mcorg.domain.*

fun teamPage(world: World, team: Team, projects: List<SlimProject>, packs: List<ResourcePack>): String {
    return page(siteTitle = "${world.name} - ${team.name}") {
        h2 {
            + "Projects"
        }
        ul {
            for (project in projects) {
                li {
                    a {
                        href = "/worlds/${world.name}/teams/${team.id}/project/${project.id}"
                        + project.name
                    }
                }
            }
        }
        h2 {
            + "Resource Packs"
        }
        ul {
            for (pack in packs) {
                li {
                    a {
                        href = "/resourcepacks/${pack.id}"
                        + pack.name
                    }
                }
            }
        }
    }
}