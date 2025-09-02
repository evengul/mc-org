package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.ParametersBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CreateTaskPipelineTest {

    @Test
    fun `should parse empty requirements from parameters`() {
        val parameters = ParametersBuilder().apply {
            append("name", "Test Task")
            append("priority", "HIGH")
        }.build()

        val result = ValidateTaskInputStep.parseRequirementsFromParameters(parameters)
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should parse single item requirement from parameters`() {
        val parameters = ParametersBuilder().apply {
            append("name", "Test Task")
            append("priority", "HIGH")
            append("requirements[0].type", "ITEM")
            append("requirements[0].item", "Stone")
            append("requirements[0].requiredAmount", "64")
        }.build()

        val result = ValidateTaskInputStep.parseRequirementsFromParameters(parameters)
        
        assertEquals(1, result.size)
        val requirement = result[0]
        assertEquals("ITEM", requirement.type)
        assertEquals("Stone", requirement.item)
        assertEquals(64, requirement.requiredAmount)
        assertNull(requirement.action)
    }

    @Test
    fun `should parse single action requirement from parameters`() {
        val parameters = ParametersBuilder().apply {
            append("name", "Test Task")
            append("priority", "HIGH")
            append("requirements[0].type", "ACTION")
            append("requirements[0].action", "Build foundation")
        }.build()

        val result = ValidateTaskInputStep.parseRequirementsFromParameters(parameters)
        
        assertEquals(1, result.size)
        val requirement = result[0]
        assertEquals("ACTION", requirement.type)
        assertEquals("Build foundation", requirement.action)
        assertNull(requirement.item)
        assertNull(requirement.requiredAmount)
    }

    @Test
    fun `should parse multiple mixed requirements from parameters`() {
        val parameters = ParametersBuilder().apply {
            append("name", "Test Task")
            append("priority", "HIGH")
            append("requirements[0].type", "ITEM")
            append("requirements[0].item", "Stone")
            append("requirements[0].requiredAmount", "64")
            append("requirements[1].type", "ACTION")
            append("requirements[1].action", "Build foundation")
            append("requirements[2].type", "ITEM")
            append("requirements[2].item", "Oak Wood")
            append("requirements[2].requiredAmount", "128")
        }.build()

        val result = ValidateTaskInputStep.parseRequirementsFromParameters(parameters)
        
        assertEquals(3, result.size)
        
        // First requirement - Item
        val req1 = result[0]
        assertEquals("ITEM", req1.type)
        assertEquals("Stone", req1.item)
        assertEquals(64, req1.requiredAmount)
        
        // Second requirement - Action
        val req2 = result[1]
        assertEquals("ACTION", req2.type)
        assertEquals("Build foundation", req2.action)
        
        // Third requirement - Item
        val req3 = result[2]
        assertEquals("ITEM", req3.type)
        assertEquals("Oak Wood", req3.item)
        assertEquals(128, req3.requiredAmount)
    }

    @Test
    fun `should validate item requirements correctly`() {
        val requirements = listOf(
            TaskRequirementInput(type = "ITEM", item = "Stone", requiredAmount = 64),
            TaskRequirementInput(type = "ITEM", item = "", requiredAmount = 10), // Invalid: empty item
            TaskRequirementInput(type = "ITEM", item = "Wood", requiredAmount = 0), // Invalid: zero amount
            TaskRequirementInput(type = "ITEM", item = "Iron", requiredAmount = 1000000) // Invalid: too large
        )

        val errors = ValidateTaskInputStep.validateRequirements(requirements)
        
        assertEquals(3, errors.size)
        assertTrue(errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "requirements[1].item" })
        assertTrue(errors.any { it is ValidationFailure.InvalidFormat && it.parameterName == "requirements[2].requiredAmount" })
        assertTrue(errors.any { it is ValidationFailure.InvalidFormat && it.parameterName == "requirements[3].requiredAmount" })
    }

    @Test
    fun `should validate action requirements correctly`() {
        val requirements = listOf(
            TaskRequirementInput(type = "ACTION", action = "Build foundation"),
            TaskRequirementInput(type = "ACTION", action = ""), // Invalid: empty action
            TaskRequirementInput(type = "ACTION", action = "A".repeat(501)) // Invalid: too long
        )

        val errors = ValidateTaskInputStep.validateRequirements(requirements)
        
        assertEquals(2, errors.size)
        assertTrue(errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "requirements[1].action" })
        assertTrue(errors.any { it is ValidationFailure.InvalidLength && it.parameterName == "requirements[2].action" })
    }

    @Test
    fun `should validate invalid requirement types`() {
        val requirements = listOf(
            TaskRequirementInput(type = "INVALID_TYPE")
        )

        val errors = ValidateTaskInputStep.validateRequirements(requirements)
        
        assertEquals(1, errors.size)
        assertTrue(errors.any { it is ValidationFailure.InvalidFormat && it.parameterName == "requirements[0].type" })
    }

    @Test
    fun `should create valid CreateTaskInput with requirements`() {
        val result = runBlocking {
            val parameters = ParametersBuilder().apply {
                append("name", "Test Task")
                append("description", "Test description")
                append("priority", "HIGH")
                append("requirements[0].type", "ITEM")
                append("requirements[0].item", "Stone")
                append("requirements[0].requiredAmount", "64")
            }.build()

            ValidateTaskInputStep.process(parameters)
        }
        
        assertTrue(result is Result.Success)
        val input = result.getOrNull()!!
        assertEquals("Test Task", input.name)
        assertEquals("Test description", input.description)
        assertEquals(Priority.HIGH, input.priority)
        assertEquals(1, input.requirements.size)
        assertEquals("Stone", input.requirements[0].item)
    }

    @Test
    fun `should handle missing required fields`() {
        val result = runBlocking {
            val parameters = ParametersBuilder().apply {
                // Missing name
                append("priority", "HIGH")
            }.build()
            ValidateTaskInputStep.process(parameters)
        }
        
        assertTrue(result is Result.Failure)
        val failure = result.errorOrNull()!!
        assertTrue(failure is CreateTaskFailures.ValidationError)
        val validationError = failure as CreateTaskFailures.ValidationError
        assertTrue(validationError.errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "name" })
    }
}
