package app.mcorg.presentation.templated.common.avatar

import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.icon.iconComponent
import kotlinx.html.Tag

@Suppress("unused")
fun <T : Tag> T.avatar(size: IconSize = IconSize.MEDIUM, color: IconColor) = iconComponent(Icons.Users.PROFILE, size, color)