package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.notification.*
import app.mcorg.pipeline.failure.NotificationFailures
import app.mcorg.presentation.plugins.NotificationParamPlugin
import app.mcorg.presentation.templated.notification.notificationsPage
import app.mcorg.presentation.templated.notification.notificationItem
import app.mcorg.presentation.templated.notification.notificationsList
import app.mcorg.presentation.utils.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.slf4j.LoggerFactory

class NotificationHandler {
    private val logger = LoggerFactory.getLogger(NotificationHandler::class.java)

    fun Route.notificationRoutes() {
        route("/notifications") {
            get {
                call.handleGetNotifications()
            }
            patch("/read") {
                call.handleMarkAllNotificationsRead()
            }
            route("/{notificationId}") {
                install(NotificationParamPlugin)
                patch("/read") {
                    call.handleMarkNotificationRead()
                }
            }
        }
    }

    private suspend fun ApplicationCall.handleGetNotifications() {
        val user = getUser()
        val unreadOnly = parameters["unread"]?.toBoolean() ?: false
        val limit = parameters["limit"]?.toIntOrNull() ?: 20
        val offset = parameters["offset"]?.toIntOrNull() ?: 0

        val input = GetNotificationsInput(
            userId = user.id,
            limit = limit,
            offset = offset,
            unreadOnly = unreadOnly
        )

        executePipeline(
            onSuccess = { notifications ->
                val unreadCount = notifications.count { it.readAt == null }
                respondHtml(notificationsPage(user, notifications, unreadOnly, unreadCount))
            },
            onFailure = { failure ->
                logger.error("Failed to get notifications for user ${user.id}: $failure")
                respondBadRequest("Failed to load notifications")
            }
        ) {
            step(Step.value(input))
                .step(GetUserNotificationsStep)
        }
    }

    private suspend fun ApplicationCall.handleMarkNotificationRead() {
        val user = getUser()
        val notificationId = getNotificationId()

        val input = MarkNotificationInput(
            userId = user.id,
            notificationId = notificationId
        )

        executePipeline(
            onSuccess = { notification ->
                respondHtml(createHTML().div {
                    div("notice notice--success") {
                        +"Notification marked as read"
                    }
                    // Return updated notification item
                    notificationItem(notification)
                })
            },
            onFailure = { failure ->
                val errorMessage = when (failure) {
                    is NotificationFailures.NotificationNotFound -> "Notification not found"
                    is NotificationFailures.InsufficientPermissions -> "Permission denied"
                    is NotificationFailures.DatabaseError -> "Failed to update notification"
                    else -> "An error occurred"
                }
                logger.error("Failed to mark notification as read for user ${user.id}: $failure")
                respondBadRequest(errorMessage)
            }
        ) {
            step(Step.value(input))
                .step(MarkNotificationReadStep)
        }
    }

    private suspend fun ApplicationCall.handleMarkAllNotificationsRead() {
        val user = getUser()

        executePipeline(
            onSuccess = { result ->
                respondHtml(createHTML().div {
                    div("notice notice--success") {
                        +"All notifications marked as read (${result.updatedCount} updated)"
                    }
                    // Return updated notifications list
                    notificationsList(result.notifications)
                })
            },
            onFailure = { failure ->
                logger.error("Failed to mark all notifications as read for user ${user.id}: $failure")
                respondBadRequest("Failed to update notifications")
            }
        ) {
            step(Step.value(user.id))
                .step(BulkMarkNotificationsReadStep)
        }
    }
}