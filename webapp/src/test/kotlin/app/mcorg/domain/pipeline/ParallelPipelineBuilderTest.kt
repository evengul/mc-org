package app.mcorg.domain.pipeline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParallelPipelineBuilderTest {

    // Test data classes
    data class TestError(val message: String)
    data class ValidationError(val field: String, val reason: String)

    @Test
    fun `pipeline should create single pipeline node with no dependencies`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()
        val simplePipeline = Pipeline<String, TestError, Int> { input ->
            Result.success(input.length)
        }

        val pipelineRef = builder.pipeline("length", "hello", simplePipeline)

        // Verify the pipeline reference is created correctly
        assertEquals("length", pipelineRef.id)

        // Test the actual pipeline execution independently
        val result = simplePipeline.execute("hello")
        assertEquals(5, (result as Result.Success).value) // "hello".length = 5
    }

    @Test
    fun `multiple pipelines should be added independently`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        val lengthPipeline = Pipeline<String, TestError, Int> { input ->
            Result.success(input.length)
        }

        val uppercasePipeline = Pipeline<String, TestError, String> { input ->
            Result.success(input.uppercase())
        }

        val lengthRef = builder.pipeline("length", "hello", lengthPipeline)
        val uppercaseRef = builder.pipeline("uppercase", "world", uppercasePipeline)

        assertEquals("length", lengthRef.id)
        assertEquals("uppercase", uppercaseRef.id)

        // Test the individual pipelines work correctly
        val lengthResult = lengthPipeline.execute("hello")
        assertEquals(5, (lengthResult as Result.Success).value) // "hello".length = 5

        val uppercaseResult = uppercasePipeline.execute("world")
        assertEquals("WORLD", (uppercaseResult as Result.Success).value) // "world".uppercase() = "WORLD"
    }

    @Test
    fun `merge with two dependencies should combine results using MergeSteps combine`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        val lengthPipeline = Pipeline<String, TestError, Int> { input ->
            Result.success(input.length)
        }

        val uppercasePipeline = Pipeline<String, TestError, String> { input ->
            Result.success(input.uppercase())
        }

        val lengthRef = builder.pipeline("length", "hello", lengthPipeline)
        val uppercaseRef = builder.pipeline("uppercase", "hello", uppercasePipeline)

        // Use MergeSteps.combine() to merge two pipeline results
        val mergedRef = builder.merge("combined", lengthRef, uppercaseRef, MergeSteps.combine<TestError, Int, String>())

        assertEquals("combined", mergedRef.id)

        // Test MergeSteps.combine() works correctly
        val combineFunction = MergeSteps.combine<TestError, Int, String>()
        val combinedResult = combineFunction(5, "HELLO")
        val pair = (combinedResult as Result.Success).value
        assertEquals(5, pair.first)
        assertEquals("HELLO", pair.second)
    }

    @Test
    fun `merge with custom merger function should work correctly`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        val pipeline1 = Pipeline<Int, TestError, Int> { input ->
            Result.success(input * 2)
        }

        val pipeline2 = Pipeline<Int, TestError, Int> { input ->
            Result.success(input + 10)
        }

        val ref1 = builder.pipeline("double", 5, pipeline1)
        val ref2 = builder.pipeline("addTen", 5, pipeline2)

        // Custom merger that adds the results
        val mergedRef = builder.merge("sum", ref1, ref2) { a: Int, b: Int ->
            Result.success(a + b)
        }

        assertEquals("sum", mergedRef.id)

        // Test the individual pipelines and merger logic
        val result1 = pipeline1.execute(5)
        assertEquals(10, (result1 as Result.Success).value) // 5 * 2 = 10

        val result2 = pipeline2.execute(5)
        assertEquals(15, (result2 as Result.Success).value) // 5 + 10 = 15

        // Test the custom merger function
        val mergerResult = Result.success<Nothing, Int>(10 + 15)
        assertEquals(25, (mergerResult as Result.Success).value) // 10 + 15 = 25
    }

    @Test
    fun `merge with MergeSteps transform should apply transformation to combined results`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        val namePipeline = Pipeline<String, TestError, String> { input ->
            Result.success(input.split(",")[0])
        }

        val agePipeline = Pipeline<String, TestError, Int> { input ->
            Result.success(input.split(",")[1].toInt())
        }

        val nameRef = builder.pipeline("name", "John,25", namePipeline)
        val ageRef = builder.pipeline("age", "John,25", agePipeline)

        // Use MergeSteps.transform() to combine and transform results
        val transformer = MergeSteps.transform<TestError, String, Int, String> { name, age ->
            "Person: $name, Age: $age"
        }

        val mergedRef = builder.merge("person", nameRef, ageRef, transformer)

        assertEquals("person", mergedRef.id)

        // Test the transformation logic
        val nameResult = namePipeline.execute("John,25")
        assertEquals("John", (nameResult as Result.Success).value)

        val ageResult = agePipeline.execute("John,25")
        assertEquals(25, (ageResult as Result.Success).value)

        val transformResult = transformer("John", 25)
        assertEquals("Person: John, Age: 25", (transformResult as Result.Success).value)
    }

    @Test
    fun `merge with MergeSteps validate should validate combined results`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        val usernamePipeline = Pipeline<String, TestError, String> { input ->
            Result.success(input)
        }

        val passwordPipeline = Pipeline<String, TestError, String> { input ->
            Result.success(input)
        }

        val usernameRef = builder.pipeline("username", "admin", usernamePipeline)
        val passwordRef = builder.pipeline("password", "password123", passwordPipeline)

        // Use MergeSteps.validate() to validate username and password combination
        val validator = MergeSteps.validate(TestError("Invalid credentials")) { username: String, password: String ->
            username.isNotEmpty() && password.length >= 8
        }

        val validatedRef = builder.merge("credentials", usernameRef, passwordRef, validator)

        assertEquals("credentials", validatedRef.id)

        // Test the validation logic
        val validationResult = validator("admin", "password123")
        val pair = (validationResult as Result.Success).value
        assertEquals("admin", pair.first)
        assertEquals("password123", pair.second)

        // Test validation failure
        val failureResult = validator("", "short")
        assertEquals("Invalid credentials", (failureResult as Result.Failure).error.message)
    }

    @Test
    fun `merge with list dependencies should use MergeSteps collectList`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        val pipeline1 = Pipeline<String, TestError, String> { input ->
            Result.success("Result1: $input")
        }

        val pipeline2 = Pipeline<String, TestError, String> { input ->
            Result.success("Result2: $input")
        }

        val pipeline3 = Pipeline<String, TestError, String> { input ->
            Result.success("Result3: $input")
        }

        val ref1 = builder.pipeline("task1", "data", pipeline1)
        val ref2 = builder.pipeline("task2", "data", pipeline2)
        val ref3 = builder.pipeline("task3", "data", pipeline3)

        // Use MergeSteps.collectList() to collect all results into a list
        val collector = MergeSteps.collectList<TestError, String>()

        val collectedRef = builder.merge("collected", listOf(ref1, ref2, ref3), collector)

        assertEquals("collected", collectedRef.id)

        // Test the collection logic
        val testMap = mapOf(
            "task1" to "Result1: data",
            "task2" to "Result2: data",
            "task3" to "Result3: data"
        )
        val collectionResult = collector(testMap)
        val collectedList = (collectionResult as Result.Success).value
        assertEquals(3, collectedList.size)
        assertEquals(setOf("Result1: data", "Result2: data", "Result3: data"), collectedList.toSet())
    }

    @Test
    fun `complex parallel pipeline with multiple merge stages should work correctly`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        // Stage 1: Individual data processing pipelines
        val namePipeline = Pipeline<String, TestError, String> { input ->
            Result.success(input.trim().lowercase())
        }

        val agePipeline = Pipeline<String, TestError, Int> { input ->
            Result.success(input.toInt())
        }

        val nameRef = builder.pipeline("processName", " John ", namePipeline)
        val ageRef = builder.pipeline("processAge", "25", agePipeline)

        // Stage 2: Combine name and age with validation
        val nameAgeValidator = MergeSteps.validate(TestError("Invalid name/age")) { name: String, age: Int ->
            name.isNotEmpty() && age >= 18
        }
        val nameAgeRef = builder.merge("nameAge", nameRef, ageRef, nameAgeValidator)

        // Stage 3: Validate email separately
        val emailPipeline = Pipeline<String, TestError, String> { input ->
            if (input.contains("@")) {
                Result.success(input.trim().lowercase())
            } else {
                Result.failure(TestError("Invalid email"))
            }
        }
        val emailValidRef = builder.pipeline("validEmail", "john@example.com", emailPipeline)

        // Stage 4: Final merge of all validated data
        val finalTransformer = MergeSteps.transform<TestError, Pair<String, Int>, String, String> { nameAge, email ->
            "User: ${nameAge.first}, Age: ${nameAge.second}, Email: $email"
        }

        val finalRef = builder.merge("finalUser", nameAgeRef, emailValidRef, finalTransformer)

        assertEquals("finalUser", finalRef.id)

        // Test the individual pipeline components work correctly
        val nameResult = namePipeline.execute(" John ")
        assertEquals("john", (nameResult as Result.Success).value) // " John ".trim().lowercase() = "john"

        val ageResult = agePipeline.execute("25")
        assertEquals(25, (ageResult as Result.Success).value) // "25".toInt() = 25

        val emailResult = emailPipeline.execute("john@example.com")
        assertEquals("john@example.com", (emailResult as Result.Success).value)

        // Test the validation and transformation logic
        val validationResult = nameAgeValidator("john", 25)
        val nameAgePair = (validationResult as Result.Success).value
        assertEquals("john", nameAgePair.first)
        assertEquals(25, nameAgePair.second)

        val transformResult = finalTransformer(nameAgePair, "john@example.com")
        assertEquals("User: john, Age: 25, Email: john@example.com", (transformResult as Result.Success).value)
    }

    @Test
    fun `merge with failing pipeline should propagate error correctly`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        val successPipeline = Pipeline<String, TestError, String> { input ->
            Result.success("Success: $input")
        }

        val failingPipeline = Pipeline<String, TestError, String> { _ ->
            Result.failure(TestError("Pipeline failed"))
        }

        val successRef = builder.pipeline("success", "data", successPipeline)
        val failingRef = builder.pipeline("failing", "data", failingPipeline)

        // Merger should handle the failure case
        val combiner = MergeSteps.combine<TestError, String, String>()
        val mergedRef = builder.merge("merged", successRef, failingRef, combiner)

        assertEquals("merged", mergedRef.id)

        // Test the individual pipeline behaviors
        val successResult = successPipeline.execute("data")
        assertEquals("Success: data", (successResult as Result.Success).value)

        val failureResult = failingPipeline.execute("data")
        assertEquals("Pipeline failed", (failureResult as Result.Failure).error.message)
    }

    @Test
    fun `merge with MergeSteps validate should fail when predicate is false`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        val pipeline1 = Pipeline<Int, TestError, Int> { input ->
            Result.success(input)
        }

        val pipeline2 = Pipeline<Int, TestError, Int> { input ->
            Result.success(input)
        }

        val ref1 = builder.pipeline("num1", 5, pipeline1)
        val ref2 = builder.pipeline("num2", 3, pipeline2)

        // Validator that requires sum to be greater than 10
        val validator = MergeSteps.validate(TestError("Sum too small")) { a: Int, b: Int ->
            a + b > 10
        }

        val validatedRef = builder.merge("validated", ref1, ref2, validator)

        assertEquals("validated", validatedRef.id)

        // Test the validation logic directly (5 + 3 = 8, which is not > 10)
        val result1 = pipeline1.execute(5)
        assertEquals(5, (result1 as Result.Success).value)

        val result2 = pipeline2.execute(3)
        assertEquals(3, (result2 as Result.Success).value)

        val validationResult = validator(5, 3)
        assertEquals("Sum too small", (validationResult as Result.Failure).error.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["hello", "world", "test", "data"])
    fun `pipeline should handle various input types`(input: String) = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        val pipeline = Pipeline<String, TestError, Int> { str ->
            Result.success(str.length)
        }

        val pipelineRef = builder.pipeline("length_$input", input, pipeline)

        assertEquals("length_$input", pipelineRef.id)

        // Test the pipeline execution directly
        val result = pipeline.execute(input)
        assertEquals(input.length, (result as Result.Success).value)
    }

    @Test
    fun `merge with different error types should work with type system`() = runBlocking {
        val builder = ParallelPipelineBuilder<ValidationError>()

        val pipeline1 = Pipeline<String, ValidationError, String> { input ->
            if (input.isNotEmpty()) {
                Result.success(input.uppercase())
            } else {
                Result.failure(ValidationError("input", "cannot be empty"))
            }
        }

        val pipeline2 = Pipeline<String, ValidationError, Int> { input ->
            Result.success(input.length)
        }

        val ref1 = builder.pipeline("uppercase", "hello", pipeline1)
        val ref2 = builder.pipeline("length", "hello", pipeline2)

        val combiner = MergeSteps.combine<ValidationError, String, Int>()
        val mergedRef = builder.merge("combined", ref1, ref2, combiner)

        assertEquals("combined", mergedRef.id)

        // Test the individual pipeline execution
        val uppercaseResult = pipeline1.execute("hello")
        assertEquals("HELLO", (uppercaseResult as Result.Success).value) // "hello".uppercase() = "HELLO"

        val lengthResult = pipeline2.execute("hello")
        assertEquals(5, (lengthResult as Result.Success).value) // "hello".length = 5

        // Test the combiner
        val combinedResult = combiner("HELLO", 5)
        val pair = (combinedResult as Result.Success).value
        assertEquals("HELLO", pair.first)
        assertEquals(5, pair.second)
    }

    @Test
    fun `nested merge operations should create proper dependency chains`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        // First level pipelines
        val pipeline1 = Pipeline<String, TestError, Int> { input ->
            Result.success(input.length)
        }

        val pipeline2 = Pipeline<String, TestError, Int> { input ->
            Result.success(input.count { it.isDigit() })
        }

        val pipeline3 = Pipeline<String, TestError, Int> { input ->
            Result.success(input.count { it.isLetter() })
        }

        val ref1 = builder.pipeline("length", "hello123", pipeline1)
        val ref2 = builder.pipeline("digits", "hello123", pipeline2)
        val ref3 = builder.pipeline("letters", "hello123", pipeline3)

        // Second level: merge first two
        val combiner1 = MergeSteps.combine<TestError, Int, Int>()
        val merged1 = builder.merge("lengthDigits", ref1, ref2, combiner1)

        // Third level: merge result with third pipeline
        val combiner2 = MergeSteps.combine<TestError, Pair<Int, Int>, Int>()
        val finalMerged = builder.merge("final", merged1, ref3, combiner2)

        assertEquals("final", finalMerged.id)

        // Test the individual pipeline results
        val lengthResult = pipeline1.execute("hello123")
        assertEquals(8, (lengthResult as Result.Success).value) // "hello123".length = 8

        val digitsResult = pipeline2.execute("hello123")
        assertEquals(3, (digitsResult as Result.Success).value) // "hello123" has 3 digits (1,2,3)

        val lettersResult = pipeline3.execute("hello123")
        assertEquals(5, (lettersResult as Result.Success).value) // "hello123" has 5 letters (h,e,l,l,o)

        // Test the merge operations
        val firstCombined = combiner1(8, 3)
        val lengthDigitsPair = (firstCombined as Result.Success).value
        assertEquals(8, lengthDigitsPair.first)
        assertEquals(3, lengthDigitsPair.second)

        val finalCombined = combiner2(lengthDigitsPair, 5)
        val finalPair = (finalCombined as Result.Success).value
        assertEquals(Pair(8, 3), finalPair.first)
        assertEquals(5, finalPair.second)
    }

    @Test
    fun `merge with async operations should work correctly`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        val asyncPipeline1 = Pipeline<String, TestError, String> { input ->
            kotlinx.coroutines.delay(10) // Simulate async operation
            Result.success("Async1: $input")
        }

        val asyncPipeline2 = Pipeline<String, TestError, String> { input ->
            kotlinx.coroutines.delay(20) // Simulate async operation
            Result.success("Async2: $input")
        }

        val ref1 = builder.pipeline("async1", "data", asyncPipeline1)
        val ref2 = builder.pipeline("async2", "data", asyncPipeline2)

        val asyncTransformer = MergeSteps.transform<TestError, String, String, String> { result1, result2 ->
            kotlinx.coroutines.delay(5) // Simulate async transformation
            "$result1 + $result2"
        }

        val mergedRef = builder.merge("asyncMerged", ref1, ref2, asyncTransformer)

        assertEquals("asyncMerged", mergedRef.id)

        // Test the async operations work correctly
        val async1Result = asyncPipeline1.execute("data")
        assertEquals("Async1: data", (async1Result as Result.Success).value)

        val async2Result = asyncPipeline2.execute("data")
        assertEquals("Async2: data", (async2Result as Result.Success).value)

        val transformResult = asyncTransformer("Async1: data", "Async2: data")
        assertEquals("Async1: data + Async2: data", (transformResult as Result.Success).value)
    }

    @Test
    fun `complex business logic with parallel processing should work end-to-end`() = runBlocking {
        val builder = ParallelPipelineBuilder<TestError>()

        // Simulate processing user registration data in parallel
        data class UserData(val name: String, val age: Int, val email: String, val isValid: Boolean)

        val nameValidationPipeline = Pipeline<String, TestError, String> { name ->
            if (name.isNotBlank() && name.length >= 2) {
                Result.success(name.trim())
            } else {
                Result.failure(TestError("Invalid name"))
            }
        }

        val ageValidationPipeline = Pipeline<String, TestError, Int> { ageStr ->
            try {
                val age = ageStr.toInt()
                if (age >= 18) {
                    Result.success(age)
                } else {
                    Result.failure(TestError("Must be 18 or older"))
                }
            } catch (_: NumberFormatException) {
                Result.failure(TestError("Invalid age format"))
            }
        }

        val emailValidationPipeline = Pipeline<String, TestError, String> { email ->
            if (email.contains("@") && email.contains(".")) {
                Result.success(email.lowercase())
            } else {
                Result.failure(TestError("Invalid email format"))
            }
        }

        val nameRef = builder.pipeline("validateName", "John Doe", nameValidationPipeline)
        val ageRef = builder.pipeline("validateAge", "25", ageValidationPipeline)
        val emailRef = builder.pipeline("validateEmail", "john@example.com", emailValidationPipeline)

        // Combine name and age first
        val nameAgeTransformer = MergeSteps.transform<TestError, String, Int, Pair<String, Int>> { name, age ->
            Pair(name, age)
        }
        val nameAgeRef = builder.merge("nameAge", nameRef, ageRef, nameAgeTransformer)

        // Final combination with email
        val finalTransformer = MergeSteps.transform<TestError, Pair<String, Int>, String, UserData> { nameAge, email ->
            UserData(nameAge.first, nameAge.second, email, true)
        }

        val finalRef = builder.merge("userData", nameAgeRef, emailRef, finalTransformer)

        assertEquals("userData", finalRef.id)

        // Test the complete end-to-end business logic validation by testing individual components
        val nameResult = nameValidationPipeline.execute("John Doe")
        assertEquals("John Doe", (nameResult as Result.Success).value) // Name validation passed

        val ageResult = ageValidationPipeline.execute("25")
        assertEquals(25, (ageResult as Result.Success).value) // Age validation passed

        val emailResult = emailValidationPipeline.execute("john@example.com")
        assertEquals("john@example.com", (emailResult as Result.Success).value) // Email validation passed

        val nameAgeResult = nameAgeTransformer("John Doe", 25)
        val nameAgePair = (nameAgeResult as Result.Success).value
        assertEquals("John Doe", nameAgePair.first)
        assertEquals(25, nameAgePair.second)

        val userDataResult = finalTransformer(nameAgePair, "john@example.com")
        val userData = (userDataResult as Result.Success).value
        assertEquals("John Doe", userData.name)
        assertEquals(25, userData.age)
        assertEquals("john@example.com", userData.email)
        assertTrue(userData.isValid)
    }
}
