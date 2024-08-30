package app.mcorg.infrastructure.repository

import app.mcorg.domain.Profile
import at.favre.lib.crypto.bcrypt.BCrypt
import app.mcorg.domain.User
import app.mcorg.domain.Users

class UsersImpl : Users, Repository() {
    override fun userExists(id: Int): Boolean {
        return getConnection().use {
            it.prepareStatement("select 1 from users where id = ?")
                .apply { setInt(1, id) }
                .executeQuery()
                .next()
        }
    }

    override fun usernameExists(username: String): Boolean {
        return getConnection().use {
            it.prepareStatement("select 1 from users where username = ?")
                .apply { setString(1, username) }
                .executeQuery()
                .next()
        }
    }

    override fun emailExists(email: String): Boolean {
        return getConnection().use {
            it.prepareStatement("select 1 from users where email = ?")
                .apply { setString(1, email) }
                .executeQuery()
                .next()
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

    override fun getUser(username: String): User? {
        getConnection().use {
            it.prepareStatement("select id,username from users where username = ?")
                .apply { setString(1, username) }
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

    override fun createUser(username: String, email: String): Int {
        getConnection().use {
            val statement = it
                .prepareStatement("insert into users (username, email) values (?, ?) returning id")
                .apply {
                    setString(1, username)
                    setString(2, email)
                }

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

    override fun deleteUser(id: Int) {
        getConnection().use {
            it.prepareStatement("delete from users where id = ?")
                .apply { setInt(1, id) }
                .executeUpdate()
        }
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

    override fun getProfile(id: Int): Profile? {
        getConnection().use { connection ->
            connection.prepareStatement("select id,username,email,profile_photo,selected_world,technical_player from users where id=?")
                .apply { setInt(1, id) }
                .executeQuery()
                .apply {
                    if (next()) {
                        return Profile(
                            getInt(1),
                            getString(2),
                            getString(3),
                            getString(4),
                            getInt(5).takeIf { it > 0 },
                            getBoolean(6)
                        )
                    }
                }
        }
        return null
    }

    override fun selectWorld(userId: Int, worldId: Int) {
        getConnection().use {
            it.prepareStatement("update users set selected_world = ? where id = ?")
                .apply { setInt(1, worldId); setInt(2, userId) }
                .executeUpdate()
        }
    }

    override fun isTechnical(id: Int) {
        getConnection().use {
            it.prepareStatement("update users set technical_player = true where id = ?")
                .apply { setInt(1, id) }
                .executeUpdate()
        }
    }

    override fun isNotTechnical(id: Int) {
        getConnection().use {
            it.prepareStatement("update users set technical_player = false where id = ?")
                .apply { setInt(1, id) }
                .executeUpdate()
        }
    }

    private fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }
}