package no.mcorg.domain

enum class ServerType {
    VANILLA,
    FABRIC,
    FORGE
}

enum class ResourceType {
    MOD,
    MOD_PACK,
    TEXTURE_PACK,
    DATA_PACK
}

enum class Priority {
    HIGH,
    MEDIUM,
    LOW,
    NONE
}

enum class PermissionLevel {
    AUTHENTICATED,
    WORLD,
    TEAM,
    PROJECT,
    PACK
}

enum class Authority(val level: Int) {
    OWNER(0),
    ADMIN(10),
    PARTICIPANT(20)
}