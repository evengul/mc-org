package app.mcorg.pipeline.auth

import app.mcorg.test.utils.TestUtils
import app.mcorg.presentation.consts.AUTH_COOKIE
import io.ktor.server.response.ResponseCookies
import io.ktor.util.date.GMTDate
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

/**
 * Test suite for AddCookieStep - Authentication cookie management
 *
 * Tests cookie setting behavior with different host configurations:
 * - Local development (host = "false")
 * - Production domains (host = actual domain)
 * - Cookie attributes (httpOnly, expires, path, domain)
 *
 * Priority: High (Critical for authentication flow)
 */
class AddCookieStepTest {

    private lateinit var mockCookies: ResponseCookies
    private val testToken = "test.jwt.token"

    @BeforeEach
    fun setup() {
        mockCookies = mockk<ResponseCookies>(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `should set cookie without domain when host is false (local development)`() {
        // Arrange
        val addCookieStep = AddCookieStep(mockCookies, "false")

        // Act
        TestUtils.executeAndAssertSuccess(addCookieStep, testToken)

        // Assert
        verify {
            mockCookies.append(
                name = AUTH_COOKIE,
                value = testToken,
                httpOnly = true,
                expires = any<GMTDate>(),
                path = "/"
            )
        }
        verify(exactly = 0) {
            mockCookies.append(
                any<String>(),
                any<String>(),
                any(),
                any(),
                any(),
                domain = any()
            )
        }
    }

    @Test
    fun `should set cookie with domain when host is specified`() {
        // Arrange
        val host = "example.com"
        val addCookieStep = AddCookieStep(mockCookies, host)

        // Act
        TestUtils.executeAndAssertSuccess(addCookieStep, testToken)

        // Assert
        verify {
            mockCookies.append(
                name = AUTH_COOKIE,
                value = testToken,
                httpOnly = true,
                expires = any<GMTDate>(),
                path = "/",
                domain = host
            )
        }
    }

    @Test
    fun `should set cookie with production domain`() {
        // Arrange
        val host = "mcorg.app"
        val addCookieStep = AddCookieStep(mockCookies, host)

        // Act
        TestUtils.executeAndAssertSuccess(addCookieStep, testToken)

        // Assert
        verify {
            mockCookies.append(
                name = AUTH_COOKIE,
                value = testToken,
                httpOnly = true,
                expires = any<GMTDate>(),
                path = "/",
                domain = host
            )
        }
    }

    @Test
    fun `should set cookie with subdomain`() {
        // Arrange
        val host = "api.mcorg.app"
        val addCookieStep = AddCookieStep(mockCookies, host)

        // Act
        TestUtils.executeAndAssertSuccess(addCookieStep, testToken)

        // Assert
        verify {
            mockCookies.append(
                name = AUTH_COOKIE,
                value = testToken,
                httpOnly = true,
                expires = any<GMTDate>(),
                path = "/",
                domain = host
            )
        }
    }

    @Test
    fun `should handle empty token string`() {
        // Arrange
        val addCookieStep = AddCookieStep(mockCookies, "example.com")
        val emptyToken = ""

        // Act
        TestUtils.executeAndAssertSuccess(addCookieStep, emptyToken)

        // Assert
        verify {
            mockCookies.append(
                name = AUTH_COOKIE,
                value = emptyToken,
                httpOnly = true,
                expires = any<GMTDate>(),
                path = "/",
                domain = "example.com"
            )
        }
    }

    @Test
    fun `should handle long token string`() {
        // Arrange
        val addCookieStep = AddCookieStep(mockCookies, "example.com")
        val longToken = "a".repeat(1000)

        // Act
        TestUtils.executeAndAssertSuccess(addCookieStep, longToken)

        // Assert
        verify {
            mockCookies.append(
                name = AUTH_COOKIE,
                value = longToken,
                httpOnly = true,
                expires = any<GMTDate>(),
                path = "/",
                domain = "example.com"
            )
        }
    }
}
