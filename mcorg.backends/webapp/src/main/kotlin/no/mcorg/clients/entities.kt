package no.mcorg.clients

data class World(val id: String, val name: String)

data class Team(val id: String, val name: String)

data class Project(val id: String, val name: String)

data class Task(val id: String, val title: String, val description: String)

enum class PermissionLevel {
    ADMIN,
    PARTICIPANT
}