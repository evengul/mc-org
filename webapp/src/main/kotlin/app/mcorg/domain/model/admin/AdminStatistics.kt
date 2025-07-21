package app.mcorg.domain.model.admin

import java.time.ZonedDateTime

data class AdminStatistics(
    val dateRange: ClosedRange<ZonedDateTime>,
    val totalUsers: Int,
    val userChange: Int,
    val totalWorlds: Int,
    val worldChange: Int,
    val totalProjects: Int,
    val projectChange: Int,
    val totalMembers: Int,
    val memberChange: Int,
    val activeMembers: Int,
    val activeMembersChange: Int,
)
