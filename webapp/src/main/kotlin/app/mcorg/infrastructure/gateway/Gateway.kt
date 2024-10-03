package app.mcorg.infrastructure.gateway

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

open class Gateway {
    fun getJsonClient(): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
                Json {
                    ignoreUnknownKeys = true
                }
            }
        }
    }
}