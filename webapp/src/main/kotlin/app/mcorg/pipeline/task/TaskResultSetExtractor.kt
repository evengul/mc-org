package app.mcorg.pipeline.task

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.model.task.Task
import java.sql.ResultSet

fun ResultSet.toTask() = Task(
    id = getInt("id"),
    projectId = getInt("project_id"),
    name = getString("name"),
    description = getString("description"),
    stage = ProjectStage.valueOf(getString("stage")),
    priority = Priority.valueOf(getString("priority")),
    requirements = emptyList()
)