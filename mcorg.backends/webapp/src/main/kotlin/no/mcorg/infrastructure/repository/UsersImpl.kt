package no.mcorg.infrastructure.repository

import no.mcorg.domain.AppConfiguration
import no.mcorg.domain.User
import no.mcorg.domain.Users
import java.security.SecureRandom
import java.util.HexFormat
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class UsersImpl(config: AppConfiguration) : Users, Repository(config) {
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

    override fun createUser(username: String, password: String): Int {
        val statement = getConnection()
            .prepareStatement("insert into users (username, password_hash) values (?, ?) returning id")
            .apply { setString(1, username); setString(2, hashPassword(password)) }

        if (statement.execute()) {
            with(statement.resultSet) {
                if (next()) {
                    return getInt(1)
                }
            }
        }

        throw IllegalStateException("Failed to create user")
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
        val combined = "${getSalt()}cleverPassw0rd_r!gHT".toByteArray()

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val spec = PBEKeySpec(password.toCharArray(), combined, 120_000, 256)
        val key = factory.generateSecret(spec)
        return key.encoded.toHexString()

    }

    private fun getSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(32)
        random.nextBytes(salt)

        return salt.toHexString()
    }

    private fun ByteArray.toHexString(): String =
        HexFormat.of().formatHex(this)
}