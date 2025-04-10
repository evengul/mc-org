package app.mcorg.domain.api

import app.mcorg.domain.model.permissions.UserPermissions

interface Permissions {
    fun getPermissions(userId: Int): UserPermissions
}