package app.mcorg.pipeline.notification

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleMarkAllNotificationsRead() {
    val user = getUser()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().div {
                hxOutOfBands(".notification-item__mark-read-btn")
                span("notification-item__read-indicator") {
                    +"Read"
                }
            })
        },
        onFailure = { respond(HttpStatusCode.InternalServerError) }
    ) {
        value(user.id)
            .step(BulkMarkNotificationsReadStep)
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