package app.mcorg.domain.model.project

data class ProjectListItem(
    val id: Int,
    val name: String,
    val stage: ProjectStage,
    val state: ProjectState,
    val tasksTotal: Int,
    val tasksDone: Int,
    val resourcesRequired: Int,
    val resourcesGathered: Int,
    val itemCount: Int,
    val nextTaskName: String?,
    val producesCount: Int = 0,
) {
    /**
     * A finished project that declares produced items is not finished work — it is
     * running infrastructure supplying every other plan in the world (MCO-287). DONE
     * means "inert" for a build and "producing" for a farm; this is the difference,
     * and it is why the Field Log must not shelve these with the completed builds.
     */
    val isProducing: Boolean
        get() = state == ProjectState.DONE && producesCount > 0
}
