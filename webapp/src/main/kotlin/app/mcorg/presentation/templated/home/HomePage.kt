package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.utils.BreadcrumbBuilder
import kotlinx.html.id

fun homePage(
    user: TokenProfile,
    pendingInvites: List<Invite>,
    worlds: List<World>,
    supportedVersions: List<MinecraftVersion.Release>,
    unreadNotificationCount: Int = 0
) = createPage(
    user = user,
    unreadNotificationCount = unreadNotificationCount,
    breadcrumbs = BreadcrumbBuilder.buildForHome()
) {
    id = "home"
    pendingInvites.takeIf { it.isNotEmpty() }?.let {
        addComponent(PendingInvitesView(it))
    }
    addComponent(WorldsView(user, worlds, supportedVersions))
}