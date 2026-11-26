package app.mcorg.presentation.templated.dsl

sealed interface Link {
    val to: String

    object Home : Link {
        override val to: String = "/"
    }

    object Worlds : Link {
        override val to: String = "/worlds"

        fun world(id: Int) = World(id)

        data class World(val id: Int) : Link {
            override val to: String = "${Worlds.to}/$id"

            fun project(projectId: Int): Project {
                return Project(id, projectId)
            }

            /** Where a world opens (MCO-474), and since MCO-692 its only view of its projects. */
            fun roadmap(): Roadmap {
                return Roadmap(id)
            }

            @Suppress("unused")
            fun resourceMaps(): ResourceMaps {
                return ResourceMaps(id)
            }

            fun settings(): Settings {
                return Settings(id)
            }

            data class Roadmap(val worldId: Int) : Link {
                override val to: String = "/worlds/$worldId/roadmap"
            }

            data class Project(val worldId: Int, val projectId: Int) : Link {
                override val to: String = "/worlds/$worldId/projects/$projectId"

                fun tasks(): Tasks {
                    return Tasks(worldId, projectId)
                }

                fun settings(): Settings {
                    return Settings(worldId, projectId)
                }

                data class Settings(val worldId: Int, val projectId: Int) : Link {
                    override val to: String = "/worlds/$worldId/projects/$projectId/settings"
                }

                data class Tasks(val worldId: Int, val projectId: Int): Link {
                    override val to: String = "/worlds/$worldId/projects/$projectId/tasks"

                    fun task(taskId: Int): String {
                        return Tasks(worldId, projectId).to + "/$taskId"
                    }
                }
            }

            data class ResourceMaps(val worldId: Int): Link {
                override val to: String = "/worlds/$worldId/resources"
            }

            data class Settings(val worldId: Int) : Link {
                override val to: String = "/worlds/$worldId/settings"
            }
        }
    }

    object Ideas : Link {
        override val to: String = "/ideas"

        @Suppress("unused")
        fun single(id: Int): String {
            return "/ideas/$id"
        }
    }

    object Profile : Link {
        override val to: String = "/profile"
    }

    object Privacy : Link {
        override val to: String = "/privacy"
    }

    object AdminDashboard : Link {
        override val to: String = "/admin"
    }

    @Suppress("unused")
    object Servers : Link {
        override val to: String = "/servers"
    }
}
