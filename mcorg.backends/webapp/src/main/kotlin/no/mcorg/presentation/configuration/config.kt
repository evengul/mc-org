package no.mcorg.presentation.configuration

import no.mcorg.domain.*
import no.mcorg.infrastructure.repository.*

fun usersApi(): Users {
    return UsersImpl(getConfig())
}

fun permissionsApi(): Permissions {
    return PermissionsImpl(getConfig())
}

fun worldsApi(): Worlds {
    return WorldsImpl(getConfig())
}

fun teamsApi(): Teams {
    return TeamsImpl(getConfig())
}

fun projectsApi(): Projects {
    return ProjectsImpl(getConfig())
}

fun packsApi(): Packs {
    return PacksImpl(getConfig())
}

fun getConfig(): AppConfiguration {
    return AppConfiguration(
        System.getenv("DB_URL"),
        System.getenv("DB_USER"),
        System.getenv("DB_PASSWORD"),
    )
}