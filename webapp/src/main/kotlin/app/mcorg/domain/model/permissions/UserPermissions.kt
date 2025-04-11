package app.mcorg.domain.model.permissions

import app.mcorg.domain.model.worlds.World


data class UserPermissions(val userId: Int, val ownedWorlds: List<World>, val participantWorlds: List<World>)

