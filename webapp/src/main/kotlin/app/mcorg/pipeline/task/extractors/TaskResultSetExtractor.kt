package app.mcorg.pipeline.task.extractors

import app.mcorg.domain.model.task.ActionRequirement
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.task.TaskRequirement
import org.postgresql.util.PSQLException
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
        solvedByProject = try {
            getInt("solved_by_project_id").takeIf { !wasNull() }?.let {
                Pair(it, getString("solved_by_project_name"))
            }
        } catch (_: PSQLException) {
            null
        },
        requirement = toTaskRequirement()
    )
}

private fun ResultSet.toTaskRequirement(): TaskRequirement {
    return when (val type = getString("requirement_type")) {
        "ITEM" -> {
            ItemRequirement(
                itemId = getString("item_id"),
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