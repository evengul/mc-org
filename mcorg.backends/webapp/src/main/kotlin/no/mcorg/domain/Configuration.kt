package no.mcorg.domain

interface AppConfiguration {
    fun dbUrl(): String
    fun dbUser(): String
    fun dbPassword(): String
}

fun usersApi(): Users {
    TODO()
}

fun permissionsApi(): Permissions {
    TODO()
}

fun worldsApi(): Worlds {
    TODO()
}

fun teamsApi(): Teams {
    TODO()
}

fun projectsApi(): Projects {
    TODO()
}

fun packsApi(): Packs {
    TODO()
}