package app.mcorg.pipeline.permission

import app.mcorg.domain.pipeline.ContextAwareStep
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

object RemoveUserPermissionsStep : ContextAwareStep<Unit, Int, DatabaseFailure, Unit>({ _, context ->
    useConnection {
        prepareStatement("delete from permission where user_id = ?")
            .apply { setInt(1, context) }
            .executeUpdate()
        return@useConnection Result.success()
    }
})