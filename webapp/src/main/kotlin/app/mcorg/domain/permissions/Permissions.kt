package app.mcorg.domain.permissions

import app.mcorg.domain.users.User

interface Permissions {
    fun getPermissions(userId: Int): UserPermissions
    fun hasAnyWorldPermission(userId: Int): Boolean
    fun hasWorldPermission(userId: Int, authority: Authority, worldId: Int): Boolean
    fun addWorldPermission(userId: Int, worldId: Int, authority: Authority): Int
    fun changeWorldPermission(userId: Int, worldId: Int, authority: Authority)
    fun removeWorldPermission(userId: Int, worldId: Int)
    fun removeWorldPermissionForAll(worldId: Int)
    fun getUsersInWorld(worldId: Int): List<User>
    fun removeUserPermissions(userId: Int)
}