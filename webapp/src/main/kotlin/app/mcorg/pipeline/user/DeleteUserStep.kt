package app.mcorg.pipeline.user

import app.mcorg.domain.pipeline.ContextAwareStep
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

object DeleteUserStep : ContextAwareStep<Unit, Int, DatabaseFailure, Unit>({ _, context ->
    useConnection {
        prepareStatement("delete from users where id = ?")
            .apply { setInt(1, context) }
            .executeUpdate()
        return@useConnection Result.success()
    }
})