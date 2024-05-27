package no.mcorg.infrastructure.repository

import no.mcorg.domain.AppConfiguration
import java.sql.Connection
import java.sql.DriverManager

open class Repository(private val config: AppConfiguration) {
    fun getConnection(): Connection {
        return DriverManager.getConnection(config.dbUrl(), config.dbUser(), config.dbPassword())
    }
}