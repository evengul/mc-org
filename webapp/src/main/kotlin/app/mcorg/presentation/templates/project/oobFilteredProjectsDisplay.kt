package app.mcorg.presentation.templates.project

import app.mcorg.presentation.hxOutOfBands
import kotlinx.html.P
import kotlinx.html.id

fun P.oobFilteredProjectsDisplay(filtered: Int, total: Int) {
    id = "project-filter-amount"
    hxOutOfBands("#project-filter-amount")
    + "Showing $filtered of $total projects"
}