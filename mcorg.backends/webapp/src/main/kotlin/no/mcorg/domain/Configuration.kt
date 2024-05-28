package no.mcorg.domain

interface AppConfiguration {
    val dbUrl: String
    val dbUser: String
    val dbPassword: String
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