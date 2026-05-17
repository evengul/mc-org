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

            fun projects(): Projects {
                return Projects(id)
            }

            @Suppress("unused")
            fun resourceMaps(): ResourceMaps {
                return ResourceMaps(id)
            }

            fun settings(): Settings {
                return Settings(id)
            }

            data class Projects(val worldId: Int) : Link {
                override val to: String = "/worlds/$worldId/projects"
            }

            data class Project(val worldId: Int, val projectId: Int) : Link {
                override val to: String = "/worlds/$worldId/projects/$projectId"

                fun tasks(): Tasks {
                    return Tasks(worldId, projectId)
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

    object AdminDashboard : Link {
        override val to: String = "/admin"
    }

    @Suppress("unused")
    object Servers : Link {
        override val to: String = "/servers"
    }
}
