package app.mcorg.pipeline.auth

import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.domain.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test as JUnitTest

/**
 * The Microsoft OAuth `redirect_uri` (MCO-476). Microsoft compares it byte-for-byte between the
 * authorization request and the token exchange, so the value matters more than it looks.
 */
class OAuthRedirectUriTest {

    @JUnitTest
    fun `Local follows the configured port`() {
        assertEquals(
            "http://localhost:8093/auth/oidc/microsoft-redirect",
            microsoftRedirectUri(Local, host = null, port = 8093),
        )
    }

    @JUnitTest
    fun `Local on the default port is unchanged from before PORT existed`() {
        // The literal this replaced. A worktree moving off 8080 must not move production or the
        // main checkout with it.
        assertEquals(
            "http://localhost:8080/auth/oidc/microsoft-redirect",
            microsoftRedirectUri(Local, host = null, port = 8080),
        )
    }

    @JUnitTest
    fun `non-Local uses the host over HTTPS and ignores the port`() {
        assertEquals(
            "https://app.seam.gg/auth/oidc/microsoft-redirect",
            microsoftRedirectUri(Production, host = "app.seam.gg", port = 8093),
        )
        assertEquals(
            "https://mcorg-dev-1.fly.dev/auth/oidc/microsoft-redirect",
            microsoftRedirectUri(Test, host = "mcorg-dev-1.fly.dev", port = 8093),
        )
    }
}
