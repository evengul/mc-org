package app.mcorg.test.wiremock

import app.mcorg.config.AppConfig
import app.mcorg.domain.Test
import com.github.tomakehurst.wiremock.core.Options
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockClassRule
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory

class WiremockTestExtension : BeforeAllCallback {

    object Server {
        private val logger = LoggerFactory.getLogger(Server::class.java)

        val rule = WireMockClassRule(
            WireMockConfiguration.wireMockConfig()
                .dynamicPort()
                .useChunkedTransferEncoding(Options.ChunkedEncodingPolicy.NEVER)
        )

        init {
            rule.start()
            logger.info("Started WireMock server at port: ${rule.port()}")
        }
    }

    private val logger = LoggerFactory.getLogger(WiremockTestExtension::class.java)

    override fun beforeAll(p0: ExtensionContext?) {
        val url: String = Server.rule.baseUrl()

        AppConfig.modrinthBaseUrl = url
        AppConfig.microsoftLoginBaseUrl = url
        AppConfig.xboxAuthBaseUrl = url
        AppConfig.xstsAuthBaseUrl = url
        AppConfig.minecraftBaseUrl = url

        logger.info("Connected external APIs to WireMock at $url")
    }
}