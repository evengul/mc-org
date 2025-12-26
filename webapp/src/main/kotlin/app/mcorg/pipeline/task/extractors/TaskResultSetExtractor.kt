package app.mcorg.pipeline.task.extractors

import app.mcorg.domain.model.task.ActionTask
import java.sql.ResultSet

fun ResultSet.toActionTasks(): List<ActionTask> {
    return buildList {
        while (next()) {
            add(toActionTask())
        }
    }
}

fun ResultSet.toActionTask(): ActionTask {
    return ActionTask(
        id = getInt("id"),
        projectId = getInt("project_id"),
        name = getString("name"),
        completed = getBoolean("completed")
    )
}