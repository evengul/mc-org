package app.mcorg.domain.api

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.model.permissions.UserPermissions
import app.mcorg.domain.model.users.User

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