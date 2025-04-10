package app.mcorg.infrastructure.repository

import app.mcorg.domain.model.users.Profile
import app.mcorg.domain.model.users.User
import app.mcorg.domain.api.Users

class UsersImpl : Users, Repository() {

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

}