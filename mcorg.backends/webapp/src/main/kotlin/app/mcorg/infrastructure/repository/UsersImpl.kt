package app.mcorg.infrastructure.repository

import at.favre.lib.crypto.bcrypt.BCrypt
import app.mcorg.domain.User
import app.mcorg.domain.Users

class UsersImpl : Users, Repository() {
    override fun userExists(id: Int): Boolean {
        getConnection().use {
            it.prepareStatement("select 1 from users where id = ?")
                .apply { setInt(1, id) }
                .executeQuery()
                .apply { return next() }
        }
    }

    override fun getUser(id: Int): User? {
        getConnection().use {
            it.prepareStatement("select id,username from users where id = ?")
                .apply { setInt(1, id) }
                .executeQuery()
                .apply {
                    if (next()) {
                        return User(getInt(1), getString(2))
                    }
                }
        }
        return null
    }

    override fun getUserByUsernameIfPasswordMatches(username: String, password: String): User? {
        getConnection().use {
            it.prepareStatement("select id, username, password_hash from users where username = ?")
                .apply { setString(1, username) }
                .executeQuery()
                .apply {
                    if (next() && BCrypt.verifyer().verify(password.toCharArray(), getString("password_hash")).verified) {
                        return User(getInt("id"), getString("username"))
                    }
                }
        }
        return null
    }

    override fun createUser(username: String, password: String): Int {
        getConnection().use {
            val statement = it
                .prepareStatement("insert into users (username, password_hash) values (?, ?) returning id")
                .apply { setString(1, username); setString(2, hashPassword(password)) }

            if (statement.execute()) {
                with(statement.resultSet) {
                    if (next()) {
                        return getInt(1)
                    }
                }
            }
        }

        throw IllegalStateException("Failed to create user")
    }

    override fun searchUsers(searchTerm: String): List<User> {
        getConnection().use {
            it.prepareStatement("select id,username from users where username like '%?%' limit 20")
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
    }

    private fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }
}