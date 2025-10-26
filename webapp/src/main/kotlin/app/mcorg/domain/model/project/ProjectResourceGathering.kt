package app.mcorg.domain.model.project

import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.task.Task

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

fun List<Task>.toProjectResourceGathering(): ProjectResourceGathering {
    val totalNeeded = sumOf { (it.requirement as ItemRequirement).requiredAmount }
    val totalCollected = sumOf { (it.requirement as ItemRequirement).collected }
    val toCollect = map { item ->
        ProjectResourceGathering.Resource(
            name = item.name,
            needed = (item.requirement as ItemRequirement).requiredAmount,
            collected = item.requirement.collected,
        )
    }

    return ProjectResourceGathering(
        totalNeeded = totalNeeded,
        totalCollected = totalCollected,
        toCollect = toCollect
    )
}
