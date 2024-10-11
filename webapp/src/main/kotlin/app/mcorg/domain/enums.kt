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

enum class DifficultyLevel(val displayName: String) {
    START("Start of game"),
    MID("Midgame"),
    END_GAME("Endgame"),
    TECHNICAL_UNDERSTANDING_RECOMMENDED("Technical understanding recommended"),
    TECHNICAL_UNDERSTANDING_REQUIRED("Technical understanding required"),
}

enum class PlayerSetup {
    NONE,
    SWEEPING_EDGE,
    BANE_OF_ARTHROPODS,
    SMITE,
    FIRE_ASPECT,
    KNOCKBACK,
    LOOTING,
    PROTECTION,
    FIRE_PROTECTION,
    BLAST_PROTECTION,
    PROJECTILE_PROTECTION
}

enum class BeaconSetup(val main: BeaconSetup? = null) {
    SPEED_ONE,
    HASTE_ONE,
    RESISTANCE_ONE,
    JUMP_BOOST_ONE,
    STRENGTH_ONE,
    REGENERATION,
    SPEED_TWO(SPEED_ONE),
    HASTE_TWO(HASTE_ONE),
    RESISTANCE_TWO(RESISTANCE_ONE),
    JUMP_BOOST_TWO(JUMP_BOOST_ONE),
    STRENGTH_TWO(STRENGTH_ONE),
}

enum class YLevel {
    LOWEST_POSSIBLE,
    HIGHEST_POSSIBLE,
    NOT_RELEVANT
}

enum class TreeFarmType {
    MUSHROOM_LARGE,
    NETHER_TREES,
    OAK,
    BIRCH,
    SPRUCE_SMALL,
    SPRUCE_LARGE,
    ACACIA,
    UNIVERSAL,
    JUNGLE_LARGE,
    JUNGLE_SMALL,
    AZALEA,
    MANGROVE,
    CHERRY_BLOSSOM,
    DARK_OAK,
    LEAVES,
    BEE_NEST,
    MOSS
}

enum class TreeFarmSize {
    ONE_BY_ONE,
    TWO_BY_TWO,
    WEIRD
}

enum class SortingSystemPeripherals {
    MASS_CRAFTER,
    FURNACE_ARRAY,
    NANO_FARMS,
    BREWERY,
    OTHER
}

enum class Tileability {
    ONE_WIDE,
    TWO_WIDE,
    THREE_PLUS_WIDE,
    AB_ONE_WIDE,
    AB_TWO_WIDE,
    AB_THREE_PLUS_WIDE,
    ABC_ONE_WIDE,
    ABC_TWO_WIDE,
    ABC_THREE_PLUS_WIDE,
    OTHER
}

enum class SlimestoneEngineType {
    ONE_WAY,
    TWO_WAY,
    THREE_WAY,
    FOUR_WAY
}