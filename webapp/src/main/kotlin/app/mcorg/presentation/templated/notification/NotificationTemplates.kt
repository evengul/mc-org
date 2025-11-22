package app.mcorg.presentation.templated.notification

import app.mcorg.domain.model.notification.Notification
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.utils.formatAsRelativeOrDate
import app.mcorg.presentation.utils.BreadcrumbBuilder
import kotlinx.html.*

fun notificationsPage(
    user: TokenProfile,
    notifications: List<Notification>,
    unreadOnly: Boolean = false,
    unreadNotificationCount: Int = notifications.count { it.readAt == null }
) = createPage(
    user = user,
    pageTitle = "Notifications",
    unreadNotificationCount = unreadNotificationCount,
    breadcrumbs = BreadcrumbBuilder.buildForNotifications()
) {
    classes += "notifications-page"

    div("container") {
        div("notifications-header") {
            h1 { +"Notifications" }

            // Filter tabs
            div("u-flex u-flex-row u-flex-gap-sm") {
                div("tabs") {
                    a(href = "/app/notifications", classes = if (!unreadOnly) "tab tab--active" else "tab") {
                        +"All"
                    }
                    a(href = "/app/notifications?unread=true", classes = if (unreadOnly) "tab tab--active" else "tab") {
                        +"Unread"
                    }
                }

                // Mark all as read button (only show if there are unread notifications)
                if (notifications.any { it.readAt == null }) {
                    button(classes = "btn btn--neutral") {
                        attributes["hx-patch"] = "/app/notifications/read"
                        attributes["hx-target"] = "#notifications-list"
                        attributes["hx-confirm"] = "Mark all notifications as read?"
                        +"Mark All Read"
                    }
                }
            }
        }

        // Notifications list
        div("notifications-list") {
            id = "notifications-list"
            notificationsList(notifications)
        }

        // Empty state
        if (notifications.isEmpty()) {
            notificationsEmptyState(unreadOnly)
        }
    }
}

fun DIV.notificationsList(notifications: List<Notification>) {
    if (notifications.isNotEmpty()) {
        div("list") {
            notifications.forEach { notification ->
                notificationItem(notification)
            }
        }
    }
}

fun DIV.notificationItem(notification: Notification) {
    div("list__item notification-item ${if (notification.readAt == null) "notification-item--unread" else "notification-item--read"}") {
        div("list__item-content") {
            div("notification-item__header") {
                h4("list__item-title notification-item__title") {
                    +notification.title
                }
                span("notification-item__timestamp") {
                    + notification.sentAt.formatAsRelativeOrDate()
                }
            }
            p("list__item-meta notification-item__description") {
                +notification.description
            }

            // Show link if available
            notification.link?.let { link ->
                a(href = link, classes = "notification-item__link") {
                    +"View Details"
                }
            }
        }

        div("list__item-actions") {
            // Mark as read button (only show for unread notifications)
            if (notification.readAt == null) {
                button(classes = "btn btn--sm btn--neutral notification-item__mark-read-btn") {
                    attributes["hx-patch"] = "/app/notifications/${notification.id}/read"
                    +"Mark Read"
                }
            }

            // Read indicator
            if (notification.readAt != null) {
                span("notification-item__read-indicator") {
                    +"Read"
                }
            }
        }
    }
}

fun DIV.notificationsEmptyState(unreadOnly: Boolean) {
    div("notice notice--info") {
        div("notice__body") {
            if (unreadOnly) {
                p { +"You have no unread notifications." }
                a(href = "/app/notifications") {
                    +"View all notifications"
                }
            } else {
                p { +"You have no notifications yet." }
                p { +"You'll receive notifications for invitations, project updates, and other important events." }
            }
        }
    }
}
