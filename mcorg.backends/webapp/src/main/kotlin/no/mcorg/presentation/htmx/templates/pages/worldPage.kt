package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.a
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.ul
import no.mcorg.domain.ResourcePack
import no.mcorg.domain.Team
import no.mcorg.domain.World

fun worldPage(world: World, teams: List<Team>, packs: List<ResourcePack>): String {
    return page(title = world.name) {
        h2 {
            + "Teams"
        }
        ul {
            for(team in teams) {
                li {
                    a {
                        href = "/worlds/${world.id}/teams/${team.id}"
                        + team.name
                    }
                }
            }
        }
        h2 {
            + "Resource Packs"
        }
        ul {
            for(pack in packs) {
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