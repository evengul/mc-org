package app.mcorg.pipeline.notification

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleMarkAllNotificationsRead() {
    val user = getUser()

    handlePipeline(
        onSuccess = {
            respondHtml(createHTML().div {
                hxOutOfBands(".notification-item__mark-read-btn")
                span("notification-item__read-indicator") {
                    +"Read"
                }
            })
        }
    ) {
        BulkMarkNotificationsReadStep.run(user.id)
    }
}

private val BulkMarkNotificationsReadStep = DatabaseSteps.update<Int>(
    sql = SafeSQL.update("""
                UPDATE notifications 
                SET read_at = CURRENT_TIMESTAMP 
                WHERE user_id = ? AND read_at IS NULL
            """.trimIndent()),
    parameterSetter = { statement, userId ->
        statement.setInt(1, userId)
    }
)