package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.ContextAwareStep
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

object RemoveUserAssignmentsStep : ContextAwareStep<Unit, Int, DatabaseFailure, Unit>({ _, context ->
    useConnection {
        prepareStatement("update task set assignee = null where assignee = ?")
            .apply { setInt(1, context) }
            .executeUpdate()
        prepareStatement("update project set assignee = null where assignee = ?")
            .apply { setInt(1, context); }
            .executeUpdate()
        return@useConnection Result.Companion.success(Unit)
    }
})