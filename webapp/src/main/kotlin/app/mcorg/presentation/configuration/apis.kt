package app.mcorg.presentation.configuration

import app.mcorg.infrastructure.gateway.MicrosoftImpl
import app.mcorg.infrastructure.gateway.ModrinthGatewayImpl
import app.mcorg.infrastructure.reader.BiomeReader
import app.mcorg.infrastructure.reader.ItemReader
import app.mcorg.infrastructure.reader.MobReader
import app.mcorg.infrastructure.repository.*

val usersApi = UsersImpl()
val permissionsApi = PermissionsImpl()
val worldsApi = WorldsImpl()
val projectsApi = ProjectsImpl()
val contraptionsApi = ContraptionsImpl()

val biomeApi = BiomeReader()
val mobApi = MobReader()
val itemApi = ItemReader()

val minecraftApi = MicrosoftImpl()

val modrinthApi = ModrinthGatewayImpl(
    System.getenv("MODRINTH_URI")
)