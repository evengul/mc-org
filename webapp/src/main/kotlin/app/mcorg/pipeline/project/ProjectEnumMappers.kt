package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.projects.Priority
import app.mcorg.domain.model.task.TaskStage
import app.mcorg.domain.model.task.TaskStages
import app.mcorg.domain.model.task.TaskType

object ProjectEnumMappers {
    fun String.toPriority(): Priority {
        when(this) {
            "NONE" -> return Priority.NONE
            "LOW" -> return Priority.LOW
            "MEDIUM" -> return Priority.MEDIUM
            "HIGH" -> return Priority.HIGH
        }
        return Priority.NONE
    }

    fun String.toDimension(): Dimension {
        when(this) {
            "OVERWORLD" -> return Dimension.OVERWORLD
            "NETHER" -> return Dimension.NETHER
            "THE_END" -> return Dimension.THE_END
        }
        return Dimension.OVERWORLD
    }

    fun String.toTaskType(): TaskType {
        when(this) {
            "COUNTABLE" -> return TaskType.COUNTABLE
            "DOABLE" -> return TaskType.DOABLE
        }
        return TaskType.COUNTABLE
    }

    fun String.toTaskStage(): TaskStage {
        when(this) {
            TaskStages.TODO.id -> return TaskStages.TODO
            TaskStages.IN_PROGRESS.id-> return TaskStages.IN_PROGRESS
            TaskStages.DONE.id -> return TaskStages.DONE
        }
        return TaskStages.TODO
    }
}