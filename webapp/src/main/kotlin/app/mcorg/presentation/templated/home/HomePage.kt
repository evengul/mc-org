package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.utils.BreadcrumbBuilder
import kotlinx.html.id

data class HomePageData(
    val user: TokenProfile,
    val pendingInvites: List<Invite>,
    val worlds: List<World>,
    val supportedVersions: List<MinecraftVersion.Release>,
    val unreadNotificationCount: Int = 0
)

fun homePage(data: HomePageData) = createPage(
    user = data.user,
    unreadNotificationCount = data.unreadNotificationCount,
    breadcrumbs = BreadcrumbBuilder.buildForHome()
) {
    id = "home"
    data.pendingInvites.takeIf { it.isNotEmpty() }?.let {
        addComponent(PendingInvitesView(it))
    }
    addComponent(WorldsView(data.user, data.worlds, data.supportedVersions))
}