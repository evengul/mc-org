package app.mcorg.domain.model.permissions

enum class Authority(val level: Int) {
    OWNER(0),
    ADMIN(10),
    PARTICIPANT(20);

    companion object {
        fun fromLevel(level: Int): Authority? {
            return values().firstOrNull { it.level == level }
        }
    }
}