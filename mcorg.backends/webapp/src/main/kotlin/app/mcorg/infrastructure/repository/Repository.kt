package app.mcorg.infrastructure.repository

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection

private val dataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = System.getenv("DB_URL")
    username = System.getenv("DB_USER")
    password = System.getenv("DB_PASSWORD")
    driverClassName = "org.postgresql.Driver"
})

open class Repository {
    fun getConnection(): Connection {
        return dataSource.connection
    }
}