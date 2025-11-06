package app.mcorg.pipeline.auth

import app.mcorg.pipeline.auth.commonsteps.GetTokenStep
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.test.utils.TestUtils
import io.ktor.server.request.*
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test suite for GetTokenStep - Authentication token extraction from cookies
 *
 * Tests token extraction from HTTP request cookies:
 * - Successful token extraction from default AUTH_COOKIE
 * - Successful extraction from custom cookie names
 * - Missing cookie scenarios
 * - Empty cookie value handling
 *
 * Priority: High (Critical for authentication token validation)
 */
class GetTokenStepTest {

    private lateinit var mockRequestCookies: RequestCookies
    private val testToken = "test.jwt.token.123"

    @BeforeEach
    fun setup() {
        mockRequestCookies = mockk<RequestCookies>()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `should successfully extract token from default AUTH_COOKIE`() {
        // Arrange
        val getTokenStep = GetTokenStep()
        every { mockRequestCookies[AUTH_COOKIE] } returns testToken

        // Act
        val result = TestUtils.executeAndAssertSuccess(getTokenStep, mockRequestCookies)

        // Assert
        assertEquals(testToken, result)
        verify { mockRequestCookies[AUTH_COOKIE] }
    }

    @Test
    fun `should successfully extract token from custom cookie name`() {
        // Arrange
        val customCookieName = "custom_auth_token"
        val getTokenStep = GetTokenStep(customCookieName)
        every { mockRequestCookies[customCookieName] } returns testToken

        // Act
        val result = TestUtils.executeAndAssertSuccess(getTokenStep, mockRequestCookies)

        // Assert
        assertEquals(testToken, result)
        verify { mockRequestCookies[customCookieName] }
    }

    @Test
    fun `should successfully extract long JWT token`() {
        // Arrange
        val longToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.example.signature"
        val getTokenStep = GetTokenStep()
        every { mockRequestCookies[AUTH_COOKIE] } returns longToken

        // Act
        val result = TestUtils.executeAndAssertSuccess(getTokenStep, mockRequestCookies)

        // Assert
        assertEquals(longToken, result)
    }

    @Test
    fun `should successfully extract token with special characters`() {
        // Arrange
        val tokenWithSpecialChars = "token-with_special.chars123!@#"
        val getTokenStep = GetTokenStep()
        every { mockRequestCookies[AUTH_COOKIE] } returns tokenWithSpecialChars

        // Act
        val result = TestUtils.executeAndAssertSuccess(getTokenStep, mockRequestCookies)

        // Assert
        assertEquals(tokenWithSpecialChars, result)
    }

    @Test
    fun `should successfully extract empty token value`() {
        // Arrange
        val emptyToken = ""
        val getTokenStep = GetTokenStep()
        every { mockRequestCookies[AUTH_COOKIE] } returns emptyToken

        // Act
        val result = TestUtils.executeAndAssertSuccess(getTokenStep, mockRequestCookies)

        // Assert
        assertEquals(emptyToken, result)
    }

    @Test
    fun `should fail with MissingCookie when AUTH_COOKIE is not present`() {
        // Arrange
        val getTokenStep = GetTokenStep()
        every { mockRequestCookies[AUTH_COOKIE] } returns null

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            getTokenStep,
            mockRequestCookies
        )
        verify { mockRequestCookies[AUTH_COOKIE] }
    }

    @Test
    fun `should fail with MissingCookie when custom cookie is not present`() {
        // Arrange
        val customCookieName = "non_existent_cookie"
        val getTokenStep = GetTokenStep(customCookieName)
        every { mockRequestCookies[customCookieName] } returns null

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            getTokenStep,
            mockRequestCookies
        )
        verify { mockRequestCookies[customCookieName] }
    }

    @Test
    fun `should handle multiple cookie access attempts correctly`() {
        // Arrange
        val getTokenStep = GetTokenStep()
        every { mockRequestCookies[AUTH_COOKIE] } returns testToken

        // Act - Call multiple times
        val result1 = TestUtils.executeAndAssertSuccess(getTokenStep, mockRequestCookies)
        val result2 = TestUtils.executeAndAssertSuccess(getTokenStep, mockRequestCookies)

        // Assert
        assertEquals(testToken, result1)
        assertEquals(testToken, result2)
        verify(exactly = 2) { mockRequestCookies[AUTH_COOKIE] }
    }

    @Test
    fun `should handle different cookie names in same test`() {
        // Arrange
        val cookieName1 = "auth_token_1"
        val cookieName2 = "auth_token_2"
        val token1 = "token_1"
        val token2 = "token_2"

        val getTokenStep1 = GetTokenStep(cookieName1)
        val getTokenStep2 = GetTokenStep(cookieName2)

        every { mockRequestCookies[cookieName1] } returns token1
        every { mockRequestCookies[cookieName2] } returns token2

        // Act
        val result1 = TestUtils.executeAndAssertSuccess(getTokenStep1, mockRequestCookies)
        val result2 = TestUtils.executeAndAssertSuccess(getTokenStep2, mockRequestCookies)

        // Assert
        assertEquals(token1, result1)
        assertEquals(token2, result2)
        verify { mockRequestCookies[cookieName1] }
        verify { mockRequestCookies[cookieName2] }
    }
}
