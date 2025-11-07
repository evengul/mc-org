package app.mcorg.pipeline.notification

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.getNotificationId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleMarkNotificationRead() {
    val user = getUser()
    val notificationId = getNotificationId()

    executePipeline(
        onSuccess = { _ ->
            respondHtml(createHTML().div {
                span("notification-item__read-indicator") {
                    + "Read"
                }
            })
        }
    ) {
        value(notificationId)
            .step(MarkNotificationReadStep(user.id))
    }
}

private data class MarkNotificationReadStep(val userId: Int) : Step<Int, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.update("""
                UPDATE notifications 
                SET read_at = CURRENT_TIMESTAMP 
                WHERE id = ? AND user_id = ? AND read_at IS NULL
            """.trimIndent()),
            parameterSetter = { statement, notificationId ->
                statement.setInt(1, notificationId)
                statement.setInt(2, userId)
            }
        ).process(input).map {  }
    }
}