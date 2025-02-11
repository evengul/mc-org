package app.mcorg.presentation.configuration

import app.mcorg.domain.minecraft.Minecraft
import app.mcorg.domain.permissions.Permissions
import app.mcorg.domain.projects.Projects
import app.mcorg.domain.users.Users
import app.mcorg.domain.worlds.Worlds
import app.mcorg.infrastructure.gateway.MinecraftImpl
import app.mcorg.infrastructure.repository.PermissionsImpl
import app.mcorg.infrastructure.repository.ProjectsImpl
import app.mcorg.infrastructure.repository.UsersImpl
import app.mcorg.infrastructure.repository.WorldsImpl

val usersApi: Users
    get() = UsersImpl()
val permissionsApi: Permissions
    get() = PermissionsImpl()
val worldsApi: Worlds
    get() = WorldsImpl()
val projectsApi: Projects
    get() = ProjectsImpl()

val minecraftApi: Minecraft
    get() = MinecraftImpl()