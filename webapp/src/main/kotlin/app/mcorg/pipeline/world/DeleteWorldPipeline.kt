package app.mcorg.pipeline.world

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getWorldId
import io.ktor.server.application.*

suspend fun ApplicationCall.handleDeleteWorld() {
    val worldId = this.getWorldId()

    executePipeline(
        onSuccess = { clientRedirect(Link.Home.to) }
    ) {
        value(worldId)
            .step(DeleteWorldStep)
    }
}

private val DeleteWorldStep = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete("DELETE FROM world WHERE id = ?"),
    parameterSetter = { statement, worldId ->
        statement.setInt(1, worldId)
    }
)