package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class BoundedVersion(override var value: Value? = null) : CategoryFilter<BoundedVersion.Value> {
    override val name: String
        get() = "Compatible version"
    override val id: String
        get() = "common.version"

    data class Value(val lowerBound: VersionBoundValue, val upperBound: VersionBoundValue?)
}

data class VersionBoundValue(val major: Int, val minor: Int)
