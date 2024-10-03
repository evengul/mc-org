package app.mcorg.presentation.utils

object Paths {
    const val STUB = "/"
    object AUTH {
        const val AUTH_STUB = "/auth"
        const val SIGN_IN = "$AUTH_STUB/sign-in"
        const val SIGN_OUT = "$AUTH_STUB/sign-out"
        const val USER = "$AUTH_STUB/user"
        const val OIDC_MICROSOFT = "$AUTH_STUB/oidc/microsoft-redirect"
    }
    object APP {
        const val STUB = "/app"
        object PROFILE {
            const val STUB = "profile"
            const val HREF = "${APP.STUB}/$STUB"
            const val PHOTO = "$HREF/photo"
            const val IS_TECHNICAL = "$HREF/is-technical"
            const val IS_NOT_TECHNICAL = "$HREF/is-not-technical"
        }
        object WORLDS {
            const val STUB = "worlds"
            const val USERS_STUB = "users"
            const val PROJECT_STUB = "projects"
            const val ADD_TASK_STUB = "add-task"
            const val TASK_STUB = "tasks"
            const val HREF = "${APP.STUB}/$STUB"
            const val ADD = "$HREF/add"
            object WORLD {
                fun href(id: String) = "$HREF/$id"
                object USERS {
                    fun href(worldId: String) = "${WORLD.href(worldId)}/$USERS_STUB"
                    fun add(worldId: String) = "${href(worldId)}/add"
                }
                object PROJECTS {
                    fun href(worldId: String) = "${WORLD.href(worldId)}/$PROJECT_STUB"
                    fun add(worldId: String) = "${href(worldId)}/add"
                    object PROJECT {
                        fun href(worldId: String, projectId: String) = "${href(worldId)}/$projectId"
                        fun assign(worldId: String, projectId: String) = "${href(worldId, projectId)}/assign"
                        object ADD_TASK {
                            fun href(worldId: String, projectId: String) = "${PROJECT.href(worldId, projectId)}/$ADD_TASK_STUB"
                            fun doable(worldId: String, projectId: String) = "${href(worldId, projectId)}/doable"
                            fun countable(worldId: String, projectId: String) = "${href(worldId, projectId)}/countable"
                        }
                        object TASKS {
                            fun href(worldId: String, projectId: String, taskId: String) = "${href(worldId, projectId)}/$TASK_STUB/$taskId"
                            fun assign(worldId: String, projectId: String, taskId: String) = "${href(worldId, projectId, taskId)}/assign"
                            fun complete(worldId: String, projectId: String, taskId: String) = "${href(worldId, projectId, taskId)}/complete"
                            fun incomplete(worldId: String, projectId: String, taskId: String) = "${href(worldId, projectId, taskId)}/incomplete"
                        }
                    }
                }
            }
        }
    }
}