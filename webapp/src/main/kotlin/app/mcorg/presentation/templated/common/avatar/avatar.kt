package app.mcorg.presentation.templated.common.avatar

import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.icon.iconComponent
import kotlinx.html.Tag

@Suppress("unused")
fun <T : Tag> T.avatar() = iconComponent(Icons.Users.PROFILE, IconSize.SMALL)