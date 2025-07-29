package app.mcorg.presentation.templated.common.link

import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.span

fun <T : Tag> T.linkComponent(
    link: Link,
    handler: (LinkComponent.() -> Unit)? = null,
) {
    val component = LinkComponent(link)
    handler?.invoke(component)
    addComponent(component)
}

enum class LinkVariant {
    NORMAL, SUBTLE, DANGER, ACTION
}

enum class LinkSize {
    SMALL, MEDIUM, LARGE
}

enum class LinkState {
    DEFAULT, DISABLED
}

class LinkComponent(
    val link: Link,
    var text: String = link.to,
    var variant: LinkVariant = LinkVariant.NORMAL,
    var size: LinkSize = LinkSize.MEDIUM,
    var state: LinkState = LinkState.DEFAULT,
    var classes: MutableSet<String> = mutableSetOf(),
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        if (state == LinkState.DISABLED) {
            container.span {
                classes = this@LinkComponent.classes + mutableSetOf("link", "link--disabled").apply {
                    // Add size variants
                    when (this@LinkComponent.size) {
                        LinkSize.SMALL -> add("link--sm")
                        LinkSize.MEDIUM -> { /* Default size */ }
                        LinkSize.LARGE -> add("link--lg")
                    }
                }
                attributes["aria-disabled"] = "true"
                + text
            }
        } else {
            container.a {
                href = link.to
                classes = this@LinkComponent.classes + mutableSetOf("link").apply {
                    // Add variant modifier classes
                    when (this@LinkComponent.variant) {
                        LinkVariant.NORMAL -> { /* Default variant */ }
                        LinkVariant.SUBTLE -> add("link--subtle")
                        LinkVariant.DANGER -> add("link--danger")
                        LinkVariant.ACTION -> add("link--action")
                    }

                    // Add size variants
                    when (this@LinkComponent.size) {
                        LinkSize.SMALL -> add("link--sm")
                        LinkSize.MEDIUM -> { /* Default size */ }
                        LinkSize.LARGE -> add("link--lg")
                    }
                }
                + text
            }
        }
    }

    operator fun String.unaryPlus() {
        text = this
    }
}

// Convenience functions for common link variants
fun <T : Tag> T.actionLink(
    link: Link,
    text: String = link.to,
    size: LinkSize = LinkSize.MEDIUM,
    handler: (LinkComponent.() -> Unit)? = null
) {
    linkComponent(link) {
        this.text = text
        this.variant = LinkVariant.ACTION
        this.size = size
        handler?.invoke(this)
    }
}

fun <T : Tag> T.subtleLink(
    link: Link,
    text: String = link.to,
    size: LinkSize = LinkSize.MEDIUM,
    handler: (LinkComponent.() -> Unit)? = null
) {
    linkComponent(link) {
        this.text = text
        this.variant = LinkVariant.SUBTLE
        this.size = size
        handler?.invoke(this)
    }
}

fun <T : Tag> T.dangerLink(
    link: Link,
    text: String = link.to,
    size: LinkSize = LinkSize.MEDIUM,
    handler: (LinkComponent.() -> Unit)? = null
) {
    linkComponent(link) {
        this.text = text
        this.variant = LinkVariant.DANGER
        this.size = size
        handler?.invoke(this)
    }
}

sealed interface Link {
    val to: String

    object Home : Link {
        override val to: String = "/"
    }

    object Worlds : Link {
        override val to: String = "/app/worlds"

        fun world(id: Int) = World(id)

        data class World(val id: Int) : Link {
            override val to: String = "${Worlds.to}/$id"

            fun project(projectId: Int): Project {
                return Project(id, projectId)
            }

            @Suppress("unused")
            fun resourceMaps(): ResourceMaps {
                return ResourceMaps(id)
            }

            fun settings(): Settings {
                return Settings(id)
            }

            data class Project(val worldId: Int, val projectId: Int) : Link {
                override val to: String = "/app/worlds/$worldId/projects/$projectId"
            }

            data class ResourceMaps(val worldId: Int): Link {
                override val to: String = "/app/worlds/$worldId/resources"
            }

            data class Settings(val worldId: Int) : Link {
                override val to: String = "/app/worlds/$worldId/settings"
            }
        }
    }

    object Ideas : Link {
        override val to: String = "/app/ideas"

        @Suppress("unused")
        fun single(id: Int): String {
            return "/app/ideas/$id"
        }
    }

    object Profile : Link {
        override val to: String = "/app/profile"
    }

    object AdminDashboard : Link {
        override val to: String = "/app/admin"
    }

    @Suppress("unused")
    object Servers : Link {
        override val to: String = "/app/servers"
    }
}