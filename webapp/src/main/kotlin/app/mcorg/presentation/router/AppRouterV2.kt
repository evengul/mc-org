package app.mcorg.presentation.router

import app.mcorg.presentation.handler.v2.AdminHandler
import app.mcorg.presentation.handler.v2.HomeHandler
import app.mcorg.presentation.handler.v2.IdeaHandler
import app.mcorg.presentation.handler.v2.InviteHandler
import app.mcorg.presentation.handler.v2.NotificationHandler
import app.mcorg.presentation.handler.v2.ProfileHandler
import app.mcorg.presentation.handler.v2.WorldHandler
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

