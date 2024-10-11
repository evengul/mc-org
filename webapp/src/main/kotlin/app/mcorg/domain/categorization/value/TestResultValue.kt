package app.mcorg.domain.categorization.value

data class TestResultValue(var mspt: Double? = null, var hardware: String? = null, var versionValue: VersionBoundValue? = null): CategoryValue {
    override val name: String
        get() = "Test results"
    override val id: String
        get() = "common.test-result"
}
