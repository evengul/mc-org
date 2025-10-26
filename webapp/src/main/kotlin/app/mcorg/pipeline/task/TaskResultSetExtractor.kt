package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.ActionRequirement
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.task.TaskProjectStage
import app.mcorg.domain.model.task.TaskRequirement
import java.sql.ResultSet

fun ResultSet.toTasks(): List<Task> {
    return buildList {
        while (next()) {
            add(toTask())
        }
    }
}

fun ResultSet.toTask(): Task {
    return Task(
        id = getInt("id"),
        projectId = getInt("project_id"),
        name = getString("name"),
        description = getString("description"),
        stage = TaskProjectStage.valueOf(getString("stage")),
        priority = Priority.valueOf(getString("priority")),
        requirement = toTaskRequirement()
    )
}

fun ResultSet.toTaskRequirement(): TaskRequirement {
    return when (val type = getString("requirement_type")) {
        "ITEM" -> {
            ItemRequirement(
                requiredAmount = getInt("requirement_item_required_amount"),
                collected = getInt("requirement_item_collected"),
            )
        }
        "ACTION" -> {
            ActionRequirement(
                completed = getBoolean("requirement_action_completed"),
            )
        }
        else -> throw IllegalStateException("Unknown requirement type: $type")
    }
}