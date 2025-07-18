package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.users.User
import app.mcorg.domain.model.v2.world.World
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.page.createPage

fun homePage(
    user: User,
    worlds: List<World>
) = createPage(user = user) {
    addComponent(WorldsView(worlds))
}