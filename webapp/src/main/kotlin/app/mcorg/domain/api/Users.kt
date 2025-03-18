package app.mcorg.domain.api

import app.mcorg.domain.model.users.Profile
import app.mcorg.domain.model.users.User

interface Users {
    fun userExists(id: Int): Boolean
    fun usernameExists(username: String): Boolean
    fun emailExists(email: String): Boolean
    fun getUser(id: Int): User?
    fun getUser(username: String): User?
    fun createUser(username: String, email: String): Int
    fun deleteUser(id: Int)
    fun searchUsers(searchTerm: String): List<User>
    fun getProfile(id: Int): Profile?
    fun selectWorld(userId: Int, worldId: Int)
    fun unSelectWorldForAll(worldId: Int)
    fun isTechnical(id: Int)
    fun isNotTechnical(id: Int)
}