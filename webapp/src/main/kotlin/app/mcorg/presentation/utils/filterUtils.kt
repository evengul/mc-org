package app.mcorg.presentation.utils

import app.mcorg.domain.SlimProject
import app.mcorg.presentation.entities.ProjectFiltersRequest
import io.ktor.http.*
import java.net.URLDecoder

fun createProjectFilters(currentUrl: String?): ProjectFiltersRequest {
    val queries = queriesToMap(currentUrl)
    return ProjectFiltersRequest(
        search = queries["search"],
        hideCompleted = queries["hideCompleted"] == "on"
    )
}

fun SlimProject.allowedByFilter(filters: ProjectFiltersRequest): Boolean {
    if (filters.hideCompleted && this.progress >= 1.0) return false
    if (filters.search?.isNotBlank() == true) {
        val search = filters.search
        val username = assignee?.username?.lowercase()
        val name = name.lowercase()
        if (username == null) {
            if (!name.contains(search)) {
                return false
            }
        } else {
            if (!username.contains(search) && !name.contains(search)) {
                return false
            }
        }
    }

    return true
}

private fun queriesToMap(currentUrl: String?): Map<String, String?> {
    if (currentUrl == null) return emptyMap()
    if (currentUrl.contains("?")) {
        return Url(currentUrl).encodedQuery.split("&")
            .map { URLDecoder.decode(it, "UTF-8") }
            .associate { parseQuery(it) }
    }
    return emptyMap()
}

private fun parseQuery(query: String): Pair<String, String?> {
    if (query.contains("=")) {
        val split = query.split("=")
        return split[0] to split[1]
    }
    return query to null
}