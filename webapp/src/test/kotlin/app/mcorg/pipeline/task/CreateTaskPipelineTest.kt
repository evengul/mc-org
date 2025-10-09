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
            append("itemRequirements[0]", """
                {
                    "name": "Stone",
                    "requiredAmount": 64
                }
            """.trimIndent())
        }.build()

        val result = ValidateTaskInputStep.parseRequirementsFromParameters(parameters)
        
        assertEquals(1, result.size)
        val requirement = result[0]
        assertTrue(requirement is TaskRequirementInput.ItemRequirementInput)
        val item = requirement as TaskRequirementInput.ItemRequirementInput
        assertEquals("Stone", requirement.name)
        assertEquals(64, item.requiredAmount)
    }

    @Test
    fun `should parse single action requirement from parameters`() {
        val parameters = ParametersBuilder().apply {
            append("name", "Test Task")
            append("priority", "HIGH")
            append("actionRequirements[0]", "Build foundation")
        }.build()

        val result = ValidateTaskInputStep.parseRequirementsFromParameters(parameters)
        
        assertEquals(1, result.size)
        val requirement = result[0]
        assertTrue(requirement is TaskRequirementInput.ActionRequirementInput)
        assertEquals("Build foundation", requirement.name)
    }

    @Test
    fun `should parse multiple mixed requirements from parameters`() {
        val parameters = ParametersBuilder().apply {
            append("name", "Test Task")
            append("priority", "HIGH")
            append("itemRequirements[0]", """
                {
                    "name": "Stone",
                    "requiredAmount": 64
                }
            """.trimIndent())
            append("actionRequirements[0]", "Build foundation")
            append("itemRequirements[1]", """
                {
                    "name": "Oak Wood",
                    "requiredAmount": 128
                }
            """.trimIndent())
        }.build()

        val result = ValidateTaskInputStep.parseRequirementsFromParameters(parameters)
        
        assertEquals(3, result.size)
        
        // First requirement - Item
        val req1 = result[0]
        assertTrue(req1 is TaskRequirementInput.ItemRequirementInput)
        val item1 = req1 as TaskRequirementInput.ItemRequirementInput
        assertEquals("Stone", item1.name)
        assertEquals(64, item1.requiredAmount)
        
        // Second requirement - Item
        val req2 = result[1]
        assertTrue(req2 is TaskRequirementInput.ItemRequirementInput)
        val item2 = req2 as TaskRequirementInput.ItemRequirementInput
        assertEquals("Oak Wood", req2.name)
        assertEquals(128, item2.requiredAmount)

        // Third requirement - Action
        val req3 = result[2]
        assertTrue(req3 is TaskRequirementInput.ActionRequirementInput)
        val action = req3 as TaskRequirementInput.ActionRequirementInput
        assertEquals("Build foundation", action.name)
    }

    @Test
    fun `should validate item requirements correctly`() {
        val requirements = listOf(
            TaskRequirementInput.ItemRequirementInput(name = "Stone", requiredAmount = 64),
            TaskRequirementInput.ItemRequirementInput(name = "", requiredAmount = 10), // Invalid: empty item
            TaskRequirementInput.ItemRequirementInput(name = "Wood", requiredAmount = 0), // Invalid: zero amount
            TaskRequirementInput.ItemRequirementInput(name = "Iron", requiredAmount = 1000000) // Invalid: too large
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
            TaskRequirementInput.ActionRequirementInput(name = "Build foundation"),
            TaskRequirementInput.ActionRequirementInput(name = ""), // Invalid: empty action
            TaskRequirementInput.ActionRequirementInput(name = "A".repeat(501)) // Invalid: too long
        )

        val errors = ValidateTaskInputStep.validateRequirements(requirements)
        
        assertEquals(2, errors.size)
        assertTrue(errors.any { it is ValidationFailure.MissingParameter && it.parameterName == "requirements[1].action" })
        assertTrue(errors.any { it is ValidationFailure.InvalidLength && it.parameterName == "requirements[2].action" })
    }

    @Test
    fun `should create valid CreateTaskInput with requirements`() {
        val result = runBlocking {
            val parameters = ParametersBuilder().apply {
                append("name", "Test Task")
                append("description", "Test description")
                append("priority", "HIGH")
                append("itemRequirements[0]", """
                {
                    "name": "Stone",
                    "requiredAmount": 64
                }
                """.trimIndent())
            }.build()

            ValidateTaskInputStep.process(parameters)
        }
        
        assertTrue(result is Result.Success)
        val input = result.getOrNull()!!
        assertEquals("Test Task", input.name)
        assertEquals("Test description", input.description)
        assertEquals(Priority.HIGH, input.priority)
        assertEquals(1, input.requirements.size)
        assertEquals("Stone", input.requirements[0].name)
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
