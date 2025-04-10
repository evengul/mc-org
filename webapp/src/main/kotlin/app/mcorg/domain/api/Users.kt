package app.mcorg.domain.api

import app.mcorg.domain.model.users.Profile
import app.mcorg.domain.model.users.User

interface Users {
    fun getUser(id: Int): User?
    fun getUser(username: String): User?
    fun createUser(username: String, email: String): Int
    fun getProfile(id: Int): Profile?
}