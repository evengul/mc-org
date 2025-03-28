package app.mcorg.domain.pipeline

import junit.framework.TestCase

class PipelineTest : TestCase() {

    fun testPipelineSuccess() {
        val step1 = MockStep()
        val step2 = MockStep()
        val pipeline = Pipeline(step1).pipe(step2)

        val result = pipeline.execute("input")

        assertTrue(result.isSuccess)
        assertEquals("Processed: Processed: input", result.getOrNull())
    }

    fun testPipelineFailure() {
        val step1 = MockStep()
        val step2 = MockStepWithFailure()
        val pipeline = Pipeline(step1).pipe(step2)

        val result = pipeline.execute("input")

        assertTrue(result.isFailure)
        assertEquals(null, result.getOrNull())
    }

    fun testPipelineWithFailure() {
        val step1 = MockStepWithFailure()
        val step2 = MockStep()
        val pipeline = Pipeline(step1).pipe(step2)

        val result = pipeline.execute("input").mapFailure {
            object : Failure {
                override fun toString(): String {
                    return "Mapped Failure"
                }
            }
        }

        assertTrue(result.isFailure)
        result.fold(
            { assertEquals("Mapped Failure", it.toString()) },
            { fail("Expected a mapped failure") }
        )
    }

    class MockStep : Step<String, Failure, String> {
        override fun process(input: String): Result<Failure, String> {
            return Result.success("Processed: $input")
        }
    }

    class MockStepWithFailure : Step<String, Failure, String> {
        override fun process(input: String): Result<Failure, String> {
            return Result.failure(SomeFailure())
        }
    }

    class SomeFailure() : Failure
}