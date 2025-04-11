package app.mcorg.domain.model.projects

data class ProjectSpecification(val search: String?, val hideCompleted: Boolean) {
    companion object {
        fun default(): ProjectSpecification {
            return ProjectSpecification(search = null, hideCompleted = false)
        }
    }
}