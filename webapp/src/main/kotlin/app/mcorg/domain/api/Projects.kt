package app.mcorg.domain.api

import app.mcorg.domain.model.projects.Project

interface Projects {
    fun getProject(id: Int, includeTasks: Boolean = false, includeDependencies: Boolean = false): Project?
    fun projectExists(id: Int): Boolean

}