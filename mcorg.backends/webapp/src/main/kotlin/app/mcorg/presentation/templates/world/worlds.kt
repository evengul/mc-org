package app.mcorg.presentation.templates.world

import app.mcorg.domain.World
import app.mcorg.presentation.templates.baseTemplate
import kotlinx.html.*

fun worlds(selectedWorldId: Int?, worlds: List<World>): String = baseTemplate {
    nav {
        button {
            + "Menu"
        }
        h1 {
            + "WORLDS"
        }
        a {
            href = "/app/worlds/add"
            button {
                + "Add"
            }
        }
    }
    main {
        ul {
            if (selectedWorldId != null) {
                val selectedWorld = worlds.find { it.id == selectedWorldId }
                if (selectedWorld != null) {
                    li {
                        + ("Selected: " + selectedWorld.name)
                    }
                }
            }
            for (world in worlds) {
                if (world.id != selectedWorldId) {
                    li {
                        + world.name
                    }
                }
            }
        }

    }
}