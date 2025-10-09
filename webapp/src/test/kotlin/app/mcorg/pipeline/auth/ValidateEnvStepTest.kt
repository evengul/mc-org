package app.mcorg.pipeline.auth

import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.domain.Test as TestEnv
import app.mcorg.pipeline.failure.ValidateEnvFailure
import app.mcorg.test.utils.TestUtils
import org.junit.jupiter.api.Test

/**
 * Test suite for ValidateEnvStep - Environment validation for authentication
 *
 * Tests environment-specific authentication rules:
 * - Local environment sign-in validation
 * - Test environment sign-in validation
 * - Production environment restrictions
 * - Environment mismatch error handling
 *
 * Priority: Medium (Required for proper environment isolation)
 */
class ValidateEnvStepTest {

    @Test
    fun `should succeed when validating Local environment with Local environment`() {
        // Arrange
        val validateStep = ValidateEnvStep(Local)
        val currentEnv = Local

        // Act
        TestUtils.executeAndAssertSuccess(validateStep, currentEnv)
    }

    @Test
    fun `should succeed when validating Test environment with Test environment`() {
        // Arrange
        val validateStep = ValidateEnvStep(TestEnv)
        val currentEnv = TestEnv

        // Act
        TestUtils.executeAndAssertSuccess(validateStep, currentEnv)
    }

    @Test
    fun `should fail when validating Local environment with Production environment`() {
        // Arrange
        val validateStep = ValidateEnvStep(Local)
        val currentEnv = Production

        // Act
        TestUtils.executeAndAssertFailure(
            validateStep,
            currentEnv,
            ValidateEnvFailure.InvalidEnv::class.java
        )
    }

    @Test
    fun `should fail when validating Test environment with Production environment`() {
        // Arrange
        val validateStep = ValidateEnvStep(TestEnv) // Expecting Test
        val currentEnv = Production // But running in Production

        // Act
        TestUtils.executeAndAssertFailure(
            validateStep,
            currentEnv,
            ValidateEnvFailure.InvalidEnv::class.java
        )
    }

    @Test
    fun `should fail when validating Local environment with Test environment`() {
        // Arrange
        val validateStep = ValidateEnvStep(Local) // Expecting Local
        val currentEnv = TestEnv // But running in Test

        // Act
        TestUtils.executeAndAssertFailure(
            validateStep,
            currentEnv,
            ValidateEnvFailure.InvalidEnv::class.java
        )
    }

    @Test
    fun `should fail when validating Test environment with Local environment`() {
        // Arrange
        val validateStep = ValidateEnvStep(TestEnv) // Expecting Test
        val currentEnv = Local // But running in Local

        // Act
        TestUtils.executeAndAssertFailure(
            validateStep,
            currentEnv,
            ValidateEnvFailure.InvalidEnv::class.java
        )
    }

    @Test
    fun `should succeed when validating Production environment with Production environment`() {
        // Arrange
        val validateStep = ValidateEnvStep(Production)
        val currentEnv = Production

        // Act
        TestUtils.executeAndAssertSuccess(
            validateStep,
            currentEnv
        )
    }
}
