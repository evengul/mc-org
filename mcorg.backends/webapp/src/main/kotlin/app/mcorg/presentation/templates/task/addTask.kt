package app.mcorg.presentation.templates.task

import app.mcorg.presentation.templates.baseTemplate
import kotlinx.html.button
import kotlinx.html.h1
import kotlinx.html.main
import kotlinx.html.nav

fun addTask() = baseTemplate {
    nav {
        button {
            + "Back"
        }
        h1 {
            + "Add task"
        }
    }
    main {
        button {
            + "Add doable"
        }
        button {
            + "Add countable"
        }
    }
}