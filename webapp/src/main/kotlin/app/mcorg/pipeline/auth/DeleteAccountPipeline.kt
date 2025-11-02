package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.layout.alert.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.layout.alert.AlertType
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getHost
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.removeToken
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.li
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleDeleteAccount() {
    val user = this.getUser()
    val host = this.getHost() ?: "false"

    executePipeline(
        onSuccess = {
            this.response.cookies.removeToken(host)
            this.clientRedirect("/")
        },
        onFailure = {
            respondHtml(createHTML().li {
                hxOutOfBands("#$ALERT_CONTAINER_ID")
                createAlert(
                    id = "delete-account-failure",
                    title = "Account Deletion Failed",
                    message = "An error occurred while trying to delete your account. Please try again later.",
                    type = AlertType.ERROR
                )
            })
        }
    ) {
        step(Step.value(user.id))
            .step(DeleteAccountStep)
    }
}

private data object DeleteAccountStep : Step<Int, DatabaseFailure, Unit> {
    override suspend fun process(input: Int): Result<DatabaseFailure, Unit> {
        return DatabaseSteps.update<Int, DatabaseFailure>(
            sql = SafeSQL.delete("DELETE FROM users WHERE id = ?"),
            parameterSetter = { statement, userId -> statement.setInt(1, userId) },
            errorMapper = { it }
        ).process(input).map {  }
    }
}