package app.mcorg.presentation.utils.url

import io.ktor.http.*
import java.net.URLDecoder

class URLMappers {
    companion object {
        internal fun queriesToMap(currentUrl: String?): Map<String, String?> {
            if (currentUrl == null) return emptyMap()
            if (currentUrl.contains("?")) {
                return Url(currentUrl).encodedQuery.split("&")
                    .map { URLDecoder.decode(it, "UTF-8") }
                    .associate { parseQuery(it) }
            }
            return emptyMap()
        }

        private fun parseQuery(query: String): Pair<String, String?> {
            if (query.contains("=")) {
                val split = query.split("=")
                return split[0] to split[1]
            }
            return query to null
        }
    }
}