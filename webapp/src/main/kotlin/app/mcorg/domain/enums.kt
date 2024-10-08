package app.mcorg.domain

enum class Dimension {
    OVERWORLD,
    NETHER,
    THE_END
}

enum class Priority {
    HIGH,
    MEDIUM,
    LOW,
    NONE
}

enum class TaskType {
    COUNTABLE,
    DOABLE
}

enum class Authority(val level: Int) {
    OWNER(0),
    ADMIN(10),
    PARTICIPANT(20)
}

enum class GameType {
    JAVA,
    BEDROCK
}

enum class ContraptionType {
    FARM,
    STORAGE
}