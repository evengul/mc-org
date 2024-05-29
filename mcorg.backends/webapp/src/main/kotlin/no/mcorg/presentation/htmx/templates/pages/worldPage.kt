package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import no.mcorg.domain.ResourcePack
import no.mcorg.domain.Team
import no.mcorg.domain.World

fun worldPage(world: World, teams: List<Team>, packs: List<ResourcePack>): String {
    return page(title = world.name) {
        h2 {
            + "Teams"
        }
        button {
            type = ButtonType.button
            + "Create new team in ${world.name}"
        }
        ul {
            for(team in teams) {
                li {
                    a {
                        href = "/worlds/${world.id}/teams/${team.id}"
                        + team.name
                    }
                    button {
                        type = ButtonType.button
                        + "Delete"
                    }
                }
            }
        }
        h2 {
            + "Resource Packs"
        }
        button {
            type = ButtonType.button
            + "Add resource pack to world"
        }
        ul {
            for(pack in packs) {
                li {
                    a {
                        href = "/resourcepacks/${pack.id}"
                        + pack.name
                    }
                    button {
                        type = ButtonType.button
                        + "Remove resourcepack from world"
                    }
                }
            }
        }
    }
}