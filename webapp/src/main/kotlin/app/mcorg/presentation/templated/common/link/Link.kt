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

enum class LinkType {
    SUBTLE,
    NORMAL,
    DISABLED,
}

class LinkComponent(
    val link: Link,
    var text: String = link.to,
    var linkType: LinkType = LinkType.NORMAL,
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        if (linkType == LinkType.DISABLED) {
            container.span("link-disabled") {
                attributes["aria-disabled"] = "true"
                + text
            }
        } else {
            container.a {
                href = link.to
                if (linkType == LinkType.SUBTLE) {
                    classes += setOf("link-subtle")
                }
                + text
            }
        }
    }

    operator fun String.unaryPlus() {
        text = this
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