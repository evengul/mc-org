package app.mcorg.presentation.templated.common.avatar

import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.icon.iconComponent
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.img
import kotlinx.html.span

enum class AvatarSize {
    SMALL, MEDIUM, LARGE, EXTRA_LARGE
}

enum class AvatarVariant {
    CIRCLE, SQUARE, ROUNDED
}

fun <T : Tag> T.avatarComponent(
    handler: (Avatar.() -> Unit)? = null
) {
    val avatar = Avatar()
    handler?.invoke(avatar)
    addComponent(avatar)
}

class Avatar(
    var imageUrl: String? = null,
    var initials: String? = null,
    var alt: String = "Avatar",
    var size: AvatarSize = AvatarSize.MEDIUM,
    var variant: AvatarVariant = AvatarVariant.CIRCLE,
    var classes: MutableSet<String> = mutableSetOf(),
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        container.div {
            // Apply base avatar class and modifiers
            classes = this@Avatar.classes + mutableSetOf("avatar").apply {
                // Add size modifier classes
                when (this@Avatar.size) {
                    AvatarSize.SMALL -> add("avatar--sm")
                    AvatarSize.MEDIUM -> { /* Default size */ }
                    AvatarSize.LARGE -> add("avatar--lg")
                    AvatarSize.EXTRA_LARGE -> add("avatar--xl")
                }

                // Add variant modifier classes
                when (this@Avatar.variant) {
                    AvatarVariant.CIRCLE -> { /* Default variant */ }
                    AvatarVariant.SQUARE -> add("avatar--square")
                    AvatarVariant.ROUNDED -> add("avatar--rounded")
                }
            }

            when {
                // Image avatar
                imageUrl != null -> {
                    img {
                        src = imageUrl!!
                        alt = this@Avatar.alt
                        classes = setOf("avatar__image")
                    }
                }
                // Initials avatar
                initials != null -> {
                    span {
                        classes = setOf("avatar__initials")
                        + initials!!
                    }
                }
                // Default icon avatar
                else -> {
                    iconComponent(
                        Icons.Users.PROFILE,
                        size = when (size) {
                            AvatarSize.SMALL -> IconSize.SMALL
                            AvatarSize.MEDIUM -> IconSize.MEDIUM
                            AvatarSize.LARGE -> IconSize.MEDIUM  // Use MEDIUM since LARGE doesn't exist
                            AvatarSize.EXTRA_LARGE -> IconSize.MEDIUM  // Use MEDIUM since LARGE doesn't exist
                        },
                        color = IconColor.ON_NEUTRAL
                    )
                }
            }
        }
    }
}

// Convenience functions for common avatar patterns
fun <T : Tag> T.imageAvatar(
    imageUrl: String,
    alt: String = "Avatar",
    size: AvatarSize = AvatarSize.MEDIUM,
    variant: AvatarVariant = AvatarVariant.CIRCLE,
    handler: (Avatar.() -> Unit)? = null
) {
    avatarComponent {
        this.imageUrl = imageUrl
        this.alt = alt
        this.size = size
        this.variant = variant
        handler?.invoke(this)
    }
}

fun <T : Tag> T.initialsAvatar(
    initials: String,
    size: AvatarSize = AvatarSize.MEDIUM,
    variant: AvatarVariant = AvatarVariant.CIRCLE,
    handler: (Avatar.() -> Unit)? = null
) {
    avatarComponent {
        this.initials = initials
        this.size = size
        this.variant = variant
        handler?.invoke(this)
    }
}

// Legacy function for backward compatibility
@Suppress("unused")
fun <T : Tag> T.avatar(size: IconSize = IconSize.MEDIUM, color: IconColor) = iconComponent(Icons.Users.PROFILE, size, color)