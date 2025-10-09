package app.mcorg.presentation.router

import app.mcorg.presentation.handler.AdminHandler
import app.mcorg.presentation.handler.HomeHandler
import app.mcorg.presentation.handler.IdeaHandler
import app.mcorg.presentation.handler.InviteHandler
import app.mcorg.presentation.handler.NotificationHandler
import app.mcorg.presentation.handler.ProfileHandler
import app.mcorg.presentation.handler.WorldHandler
import io.ktor.server.routing.Route

fun Route.appRouterV2() {
    with(HomeHandler()) {
        homeRoute()
    }

    with(ProfileHandler()) {
        profileRoutes()
    }

    with(AdminHandler()) {
        adminRoutes()
    }

    with(NotificationHandler()) {
        notificationRoutes()
    }

    with(InviteHandler()) {
        inviteRoutes()
    }

    with(WorldHandler()) {
        worldRoutes()
    }

    with(IdeaHandler()) {
        ideaRoutes()
    }
}

