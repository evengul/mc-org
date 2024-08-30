package app.mcorg.presentation.v2.configuration

import app.mcorg.domain.Worlds
import app.mcorg.infrastructure.gateway.MinecraftImpl
import app.mcorg.infrastructure.repository.PermissionsImpl
import app.mcorg.infrastructure.repository.ProjectsImpl
import app.mcorg.infrastructure.repository.UsersImpl
import app.mcorg.infrastructure.repository.WorldsImpl

val usersApi = UsersImpl()
val permissionsApi = PermissionsImpl()
val worldsApi = WorldsImpl()
val projectsApi = ProjectsImpl()

val minecraftApi = MinecraftImpl()