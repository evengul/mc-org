package app.mcorg.pipeline.auth

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.layout.alert.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.layout.alert.AlertType
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
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

private val DeleteAccountStep = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete("DELETE FROM users WHERE id = ?"),
    parameterSetter = { statement, userId -> statement.setInt(1, userId) }
)