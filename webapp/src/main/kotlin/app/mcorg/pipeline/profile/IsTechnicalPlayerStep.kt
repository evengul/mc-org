package app.mcorg.pipeline.profile

import app.mcorg.domain.pipeline.ContextAwareStep
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

object IsTechnicalPlayerStep : ContextAwareStep<Boolean, Int, DatabaseFailure, Unit>({ input, context ->
    useConnection {
        prepareStatement("update users set technical_player = ? where id = ?")
            .apply {
                setBoolean(1, input)
                setInt(2, context)
            }
            .executeUpdate()
        return@useConnection Result.success(Unit)
    }
})