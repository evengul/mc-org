package app.mcorg.domain.model.project

import app.mcorg.domain.model.task.ItemRequirement

data class ProjectResourceGathering(
    val totalNeeded: Int,
    val totalCollected: Int,
    val toCollect: List<Resource>
) {

    data class Resource(
        val name: String,
        val needed: Int,
        val collected: Int
    )
}

fun List<ItemRequirement>.toProjectResourceGathering(): ProjectResourceGathering {
    val totalNeeded = sumOf { it.requiredAmount }
    val totalCollected = sumOf { it.collected }
    val toCollect = map { item ->
        ProjectResourceGathering.Resource(
            name = item.item,
            needed = item.requiredAmount,
            collected = item.collected
        )
    }

    return ProjectResourceGathering(
        totalNeeded = totalNeeded,
        totalCollected = totalCollected,
        toCollect = toCollect
    )
}
