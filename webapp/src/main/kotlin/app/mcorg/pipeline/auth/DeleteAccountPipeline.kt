package app.mcorg.pipeline.auth

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.removeToken
import io.ktor.server.application.*

suspend fun ApplicationCall.handleDeleteAccount() {
    val user = this.getUser()
    val host = this.getHost() ?: "false"

    handlePipeline(
        onSuccess = {
            this.response.cookies.removeToken(host)
            this.clientRedirect("/")
        }
    ) {
        DeleteAccountStep.run(user.id)
    }
}

private val DeleteAccountStep = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete("DELETE FROM users WHERE id = ?"),
    parameterSetter = { statement, userId -> statement.setInt(1, userId) }
)
