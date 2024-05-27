package no.mcorg.infrastructure.repository

import no.mcorg.domain.AppConfiguration
import no.mcorg.domain.User
import no.mcorg.domain.Users

class UsersImpl(private val config: AppConfiguration) : Users, Repository(config) {
    override fun getUser(id: Int): User? {
        getConnection()
            .prepareStatement("select id,username from users where id = ?")
            .apply { setInt(1, id) }
            .executeQuery()
            .apply {
                if (next()) {
                    return User(getInt(1), getString(2))
                }
            }
        return null
    }

    override fun checkUserPassword(username: String, password: String): Boolean {
        getConnection()
            .prepareStatement("select 1 from users where password_hash = ?")
            .apply { setString(1, hashPassword(password)) }
            .executeQuery()
            .apply {
                return next()
            }
    }

    override fun createUser(username: String, password: String) {
        getConnection()
            .prepareStatement("insert into users (username, password_hash) values (?, ?)")
            .apply { setString(1, username); setString(2, hashPassword(password)) }
            .executeUpdate()
    }

    override fun searchUsers(searchTerm: String): List<User> {
        getConnection()
            .prepareStatement("select id,username from users where username like '%?%' limit 20")
            .apply { setString(1, searchTerm) }
            .executeQuery()
            .apply {
                val users = mutableListOf<User>()
                while (next()) {
                    users.add(User(getInt(1), getString(2)))
                }
                return users
            }
    }

    private fun hashPassword(password: String): String {
        TODO()
    }
}