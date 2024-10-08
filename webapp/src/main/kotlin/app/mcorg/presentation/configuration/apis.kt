package app.mcorg.presentation.configuration

import app.mcorg.infrastructure.gateway.MinecraftImpl
import app.mcorg.infrastructure.gateway.ModrinthGatewayImpl
import app.mcorg.infrastructure.repository.*

val usersApi = UsersImpl()
val permissionsApi = PermissionsImpl()
val worldsApi = WorldsImpl()
val projectsApi = ProjectsImpl()
val contraptionsApi = ContraptionsImpl()

val minecraftApi = MinecraftImpl()

val modrinthApi = ModrinthGatewayImpl(
    System.getenv("MODRINTH_URI")
)