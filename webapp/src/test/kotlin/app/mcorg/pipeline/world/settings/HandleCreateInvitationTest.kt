package app.mcorg.pipeline.world.settings

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HandleCreateInvitationTest {

    private fun createParameters(vararg pairs: Pair<String, String>): Parameters {
        val builder = ParametersBuilder()
        pairs.forEach { (key, value) -> builder.append(key, value) }
        return builder.build()
    }

    @Test
    fun `ValidateInvitationInputStep should succeed with valid input`() = runBlocking {
        // Given
        val parameters = createParameters(
            "toUsername" to "testuser",
            "role" to "MEMBER"
        )

        // When
        val result = ValidateInvitationInputStep.process(parameters)

        // Then
        assertIs<Result.Success<CreateInvitationInput>>(result)
        assertEquals("testuser", result.value.toUsername)
        assertEquals(Role.MEMBER, result.value.role)
    }

    @Test
    fun `ValidateInvitationInputStep should fail with missing toUsername`() = runBlocking {
        // Given
        val parameters = createParameters("role" to "MEMBER")

        // When
        val result = ValidateInvitationInputStep.process(parameters)

        // Then
        assertIs<Result.Failure<CreateInvitationFailures.ValidationError>>(result)
        assertTrue(result.error.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "toUsername" })
    }

    @Test
    fun `ValidateInvitationInputStep should fail with missing role`() = runBlocking {
        // Given
        val parameters = createParameters("toUsername" to "testuser")

        // When
        val result = ValidateInvitationInputStep.process(parameters)

        // Then
        assertIs<Result.Failure<CreateInvitationFailures.ValidationError>>(result)
        assertTrue(result.error.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "role" })
    }

    @ParameterizedTest
    @ValueSource(strings = ["OWNER", "BANNED"])
    fun `ValidateInvitationInputStep should reject OWNER and BANNED roles`(invalidRole: String) = runBlocking {
        // Given
        val parameters = createParameters(
            "toUsername" to "testuser",
            "role" to invalidRole
        )

        // When
        val result = ValidateInvitationInputStep.process(parameters)

        // Then
        assertIs<Result.Failure<CreateInvitationFailures.ValidationError>>(result)
        assertTrue(result.error.errors.any {
            it is ValidationFailure.InvalidFormat &&
            it.parameterName == "role" &&
            it.message == "Can only invite as MEMBER or ADMIN"
        })
    }

    @Test
    fun `ValidateInvitationInputStep should fail with invalid role format`() = runBlocking {
        // Given
        val parameters = createParameters(
            "toUsername" to "testuser",
            "role" to "INVALID_ROLE"
        )

        // When
        val result = ValidateInvitationInputStep.process(parameters)

        // Then
        assertIs<Result.Failure<CreateInvitationFailures.ValidationError>>(result)
        assertTrue(result.error.errors.any {
            it is ValidationFailure.InvalidFormat &&
            it.parameterName == "role" &&
            it.message == "Invalid role specified"
        })
    }

    @ParameterizedTest
    @ValueSource(strings = ["MEMBER", "ADMIN", "member", "admin", "Member", "Admin"])
    fun `ValidateInvitationInputStep should accept valid roles in any case`(validRole: String) = runBlocking {
        // Given
        val parameters = createParameters(
            "toUsername" to "testuser",
            "role" to validRole
        )

        // When
        val result = ValidateInvitationInputStep.process(parameters)

        // Then
        assertIs<Result.Success<CreateInvitationInput>>(result)
        assertEquals(Role.valueOf(validRole.uppercase()), result.value.role)
    }

    @Test
    fun `ValidateNotSelfInviteStep should prevent self-invitation`() = runBlocking {
        // Given
        val inviterUserId = 123
        val targetUserId = 123 // Same user ID
        val input = Pair(CreateInvitationInput("testuser", Role.MEMBER), targetUserId)
        val step = ValidateNotSelfInviteStep(inviterUserId)

        // When
        val result = step.process(input)

        // Then
        assertIs<Result.Failure<CreateInvitationFailures.CannotInviteSelf>>(result)

        return@runBlocking
    }

    @Test
    fun `ValidateNotSelfInviteStep should allow invitation to different user`() = runBlocking {
        // Given
        val inviterUserId = 123
        val targetUserId = 456 // Different user ID
        val input = Pair(CreateInvitationInput("testuser", Role.MEMBER), targetUserId)
        val step = ValidateNotSelfInviteStep(inviterUserId)

        // When
        val result = step.process(input)

        // Then
        assertIs<Result.Success<Pair<CreateInvitationInput, Int>>>(result)
        assertEquals(input, result.value)
    }
}
