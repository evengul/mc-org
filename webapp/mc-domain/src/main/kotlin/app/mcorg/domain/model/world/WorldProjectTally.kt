package app.mcorg.domain.model.world

/**
 * A world's projects counted by lifecycle state, using the same vocabulary the Field
 * Log groups by.
 *
 * Deliberately not a percentage (MCO-468). A world is never finished — you keep adding
 * projects — so "% complete" walked backwards every time one was added, and counted
 * shelved work in its denominator. These are facts a player can act on instead.
 *
 * [total] covers only the projects still on the board: cancelled and archived ones are
 * shelved and counted nowhere, so the parts always sum to the whole.
 */
data class WorldProjectTally(
    val active: Int = 0,
    val pending: Int = 0,
    val paused: Int = 0,
    val done: Int = 0,
) {
    val total: Int get() = active + pending + paused + done

    /** Work a player could pick up right now — what the hero's peek lists. */
    val onTheBoard: Int get() = active + pending + paused

    val isEmpty: Boolean get() = total == 0

    companion object {
        val EMPTY = WorldProjectTally()
    }
}
