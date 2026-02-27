package app.mcorg.domain.model.resources

data class ResourceGatheringItem(
    val id: Int,
    val projectId: Int,
    val itemId: String,
    val name: String,
    val required: Int,
    val collected: Int,
    val solvedByProject: Pair<Int, String>? = null
)