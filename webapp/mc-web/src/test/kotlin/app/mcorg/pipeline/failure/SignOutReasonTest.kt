package app.mcorg.pipeline.failure

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SignOutReasonTest {

    @Test
    fun `codes are unique`() {
        val duplicates = SignOutReason.entries.groupBy { it.code }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet(), duplicates)
    }

    @Test
    fun `an unknown or missing code falls back to the generic reason`() {
        assertEquals(SignOutReason.GENERIC, SignOutReason.fromCode("CouldNotCreateToken"))
        assertEquals(SignOutReason.GENERIC, SignOutReason.fromCode(null))
    }

    @Test
    fun `a token error's redirect carries only its reason, never the claim it failed on`() {
        val redirect = AppFailure.AuthError.ConvertTokenError.incorrectClaim("iss", "attacker-chosen").toRedirect()

        assertEquals(mapOf("error" to SignOutReason.INVALID_TOKEN.code), redirect.queryParameters)
    }

    @Test
    fun `an expired token is the one token error with its own reason`() {
        assertEquals(
            "/auth/sign-out?error=expired_token",
            AppFailure.AuthError.ConvertTokenError.expiredToken().toRedirect().toUrl(),
        )
        listOf(
            AppFailure.AuthError.ConvertTokenError.invalidToken(),
            AppFailure.AuthError.ConvertTokenError.missingClaim("sub"),
            AppFailure.AuthError.ConvertTokenError.conversionError(),
        ).forEach {
            assertEquals("/auth/sign-out?error=invalid_token", it.toRedirect().toUrl(), it.errorCode)
        }
    }
}
