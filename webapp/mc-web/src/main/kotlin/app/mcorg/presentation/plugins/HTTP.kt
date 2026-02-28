package app.mcorg.presentation.plugins

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.conditionalheaders.*

fun Application.configureHTTP() {
    if (AppConfig.env == Production) {
        install(CachingHeaders) {
            options { _, outgoingContent ->
                when (outgoingContent.contentType?.withoutParameters()) {
                    ContentType.Text.CSS -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                    ContentType.Text.JavaScript -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 15 * 60))
                    ContentType.Text.Xml -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                    ContentType.Font.Any -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                    else -> null
                }
            }
        }
    } else {
        install(CachingHeaders) {
            options { _, outgoingContent ->
                when (outgoingContent.contentType?.withoutParameters()) {
                    ContentType.Text.Xml -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                    ContentType.Font.Any -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                    else -> null
                }
            }
        }
    }
    install(ConditionalHeaders)
}
