package app.mcorg.domain.model.project

enum class ProjectStage(val order: Int) {
    IDEA(1),
    DESIGN(2),
    PLANNING(3),
    RESOURCE_GATHERING(4),
    BUILDING(5),
    TESTING(6),
    COMPLETED(7)
}
