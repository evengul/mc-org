package app.mcorg.domain.model.world

import java.time.ZonedDateTime

data class WorldStatistics(
    val dateRange: ClosedRange<ZonedDateTime>,
    val activePlayers: Int,
    val playerChange: Int,
    val playersJoined: Int,
    val playersLeft: Int,
    val projectsCreated: Int,
    val projectsCompleted: Int,
    val projectCompletionRateDays: Int
)