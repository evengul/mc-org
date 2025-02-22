package app.mcorg.domain.projects

interface TaskStage {
    val id: String
    val name: String
}

object TaskStages {
    object TODO : TaskStage {
        override val id = "TODO"
        override val name = "To Do"
    }

    object IN_PROGRESS : TaskStage {
        override val id = "IN_PROGRESS"
        override val name = "In Progress"
    }

    object DONE : TaskStage {
        override val id = "DONE"
        override val name = "Done"
    }
}