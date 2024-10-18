package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class TestResult(override var value: Value? = null): CategoryFilter<TestResult.Value> {
    override val name: String
        get() = "Test results"
    override val id: String
        get() = "common.test-result"

    data class Value(val mspt: Double, val hardware: String, val versionValue: VersionBoundValue)
}
