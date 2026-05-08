package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent
import kotlinx.html.div

enum class AvatarSize {
    SMALL,
    MEDIUM,
}

fun FlowContent.avatar(initials: String?, size: AvatarSize = AvatarSize.MEDIUM) {
    val sizeClass = when (size) {
        AvatarSize.SMALL -> ""
        AvatarSize.MEDIUM -> " avatar--md"
    }
    div("avatar$sizeClass") {
        attributes["aria-hidden"] = "true"
        +(initials?.firstOrNull()?.toString() ?: "?")
    }
}
