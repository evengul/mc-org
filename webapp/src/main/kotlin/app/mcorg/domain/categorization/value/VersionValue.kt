package app.mcorg.domain.categorization.value

data class VersionValue(var lowerBound: VersionBoundValue? = null, var upperBound: VersionBoundValue? = null) : CategoryValue {
    override val name: String
        get() = "Compatible version"
    override val id: String
        get() = "common.version"
}

fun VersionValue.upper(major: Int, minor: Int) {
    this.upperBound = VersionBoundValue(major, minor)
}

fun VersionValue.lower(major: Int, minor: Int) {
    this.lowerBound = VersionBoundValue(major, minor)
}

data class VersionBoundValue(val major: Int, val minor: Int)
