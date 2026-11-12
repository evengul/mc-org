package app.mcorg.presentation.templated.legal

import app.mcorg.presentation.handler.handleGetPrivacy
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.templated.dsl.NOT_OFFICIAL_MINECRAFT
import app.mcorg.presentation.templated.dsl.SeamOwner
import app.mcorg.presentation.templated.dsl.pageShell
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import kotlinx.html.p
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * MCO-660 and MCO-662: the Mojang line, who answers for Seam, and a privacy page someone who is
 * not signed in can read.
 */
class PrivacyPageTest {

    @Test
    fun `Every page carries the not-official line, the owner and the contact address`() {
        val html = pageShell { p { +"any page" } }

        assertContains(html, NOT_OFFICIAL_MINECRAFT)
        assertContains(html, SeamOwner.NAME)
        assertContains(html, "mailto:${SeamOwner.CONTACT_EMAIL}")
        assertContains(html, "href=\"/privacy\"")
    }

    @Test
    fun `The privacy page is readable signed out`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            install(AuthPlugin)
            get("/privacy") { call.handleGetPrivacy() }
        }

        val response = client.get("/privacy")

        assertEquals(HttpStatusCode.OK, response.status, "AuthPlugin must not bounce /privacy to sign-in")
        val body = response.bodyAsText()
        assertContains(body, "<h1 class=\"legal-page__title\">Privacy</h1>")
        assertContains(body, SeamOwner.CONTACT_EMAIL)
    }

    @Test
    fun `The privacy page can be found by search, the app cannot`() {
        assertFalse(privacyPage().contains("noindex"), "someone searching for Seam's privacy policy should find it")
        assertContains(pageShell { p { +"any page" } }, "noindex")
    }

    @Test
    fun `Only the exact path is exempt from sign-in`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            install(AuthPlugin)
            get("/privacy/anything") { call.handleGetPrivacy() }
        }

        assertEquals(HttpStatusCode.Found, client.get("/privacy/anything").status)
    }
}
