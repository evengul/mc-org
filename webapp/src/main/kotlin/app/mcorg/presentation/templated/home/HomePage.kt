package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.page.createPage
import kotlinx.html.classes
import kotlinx.html.id

fun homePage(
    user: TokenProfile,
    pendingInvites: List<Invite>,
    worlds: List<World>
) = createPage(user = user) {
    id = "home"
    addComponent(PendingInvitesView(pendingInvites))
    addComponent(WorldsView(worlds))
}