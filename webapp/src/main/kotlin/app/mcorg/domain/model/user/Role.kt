package app.mcorg.domain.model.user

enum class Role(val level: Int) {
    OWNER(0),
    ADMIN(10),
    MEMBER(100),
    BANNED(1000);

    fun isHigherThanOrEqualTo(other: Role): Boolean {
        return this.level <= other.level
    }

    fun isHigherThan(other: Role): Boolean {
        return this.level < other.level
    }

    companion object {
        fun fromString(role: String): Role {
            return entries.find { it.name.equals(role, ignoreCase = true) } ?: throw IllegalArgumentException("Unknown role: $role")
        }

        fun fromLevel(level: Int): Role {
            return entries.find { it.level == level } ?: throw IllegalArgumentException("Unknown role level: $level")
        }
    }
}