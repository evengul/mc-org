package app.mcorg.domain.permissions

import app.mcorg.domain.worlds.World


data class UserPermissions(val userId: Int, val ownedWorlds: List<World>, val participantWorlds: List<World>)

