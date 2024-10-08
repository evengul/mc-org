package app.mcorg.infrastructure.gateway

import app.mcorg.domain.Modrinth
import io.ktor.client.call.*
import io.ktor.client.request.*

class ModrinthGatewayImpl(private val baseUrl: String) : Modrinth, Gateway() {
    override suspend fun getVersions(
        includeSnapshots: Boolean,
        includeAlpha: Boolean,
        includeBeta: Boolean,
        onlyMajor: Boolean
    ): List<String> {
        getJsonClient().use { client ->
            return client.get("$baseUrl/v2/tag/game_version").body<List<ModrinthVersion>>()
                .filter { version ->
                    if (onlyMajor && !version.major) {
                        return@filter false
                    }
                    if (!includeAlpha && version.versionType == "alpha") {
                        return@filter false
                    }
                    if (!includeBeta && version.versionType == "beta") {
                        return@filter false
                    }
                    if (!includeSnapshots && version.versionType == "snapshot") {
                        return@filter false
                    }
                    return@filter true
                }.map { version -> if (version.version.count { it == '.' } == 1)
                                        version.version + ".0"
                                   else version.version }
        }
    }
}