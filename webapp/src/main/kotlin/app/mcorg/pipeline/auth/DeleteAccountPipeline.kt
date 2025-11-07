package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.removeToken
import io.ktor.server.application.*

suspend fun ApplicationCall.handleDeleteAccount() {
    val user = this.getUser()
    val host = this.getHost() ?: "false"

    executePipeline(
        onSuccess = {
            this.response.cookies.removeToken(host)
            this.clientRedirect("/")
        }
    ) {
        step(Step.value(user.id))
            .step(DeleteAccountStep)
    }
}

private val DeleteAccountStep = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete("DELETE FROM users WHERE id = ?"),
    parameterSetter = { statement, userId -> statement.setInt(1, userId) }
)