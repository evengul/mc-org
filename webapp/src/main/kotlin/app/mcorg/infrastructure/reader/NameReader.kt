package app.mcorg.infrastructure.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

abstract class NameReader(private val startsWith: String) : Reader() {
    fun getNames(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        contextClassLoader.getResourceAsStream("minecraft/named.json")
            .use { stream ->
                val bytes = (stream?.readAllBytes() ?: byteArrayOf()).toString(Charsets.UTF_8)
                val decoded = Json.decodeFromString<JsonObject>(bytes)
                decoded.keys.forEach {
                    if (decoded[it] != null && it.startsWith(startsWith)) {
                        list.add(it.replace("\"", "") to decoded[it].toString().replace("\"", ""))
                    }
                }
            }
        return list
    }

}