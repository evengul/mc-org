package app.mcorg.presentation.utils.url

import app.mcorg.domain.model.projects.ProjectSpecification

fun URLMappers.Companion.projectFilterURLMapper(url: String?): ProjectSpecification {
    val queries = queriesToMap(url)
    return ProjectSpecification(
        search = queries["search"],
        hideCompleted = queries["hideCompleted"] == "on"
    )
}