package no.mcorg.presentation.configuration

import no.mcorg.domain.*
import no.mcorg.infrastructure.repository.*

fun usersApi(): Users {
    return UsersImpl()
}

fun permissionsApi(): Permissions {
    return PermissionsImpl()
}

fun worldsApi(): Worlds {
    return WorldsImpl()
}

fun teamsApi(): Teams {
    return TeamsImpl()
}

fun projectsApi(): Projects {
    return ProjectsImpl()
}

fun packsApi(): Packs {
    return PacksImpl()
}

