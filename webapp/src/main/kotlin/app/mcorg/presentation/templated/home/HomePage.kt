package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.page.createPage

fun homePage(
    user: TokenProfile,
    worlds: List<World>
) = createPage(user = user) {
    addComponent(WorldsView(worlds))
}