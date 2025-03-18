package app.mcorg.presentation.mappers.project

import app.mcorg.domain.model.projects.ProjectSpecification
import app.mcorg.presentation.mappers.URLMappers

fun URLMappers.Companion.projectFilterURLMapper(url: String?): ProjectSpecification {
    val queries = queriesToMap(url)
    return ProjectSpecification(
        search = queries["search"],
        hideCompleted = queries["hideCompleted"] == "on"
    )
}