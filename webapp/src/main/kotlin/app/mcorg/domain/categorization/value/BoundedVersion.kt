package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class BoundedVersion(override var value: Value? = null) : CategoryFilter<BoundedVersion.Value> {
    override val name: String
        get() = "Compatible version"
    override val id: String
        get() = "common.version"

    override fun validate(): Boolean {
        val copy = value ?: return super.validate()

        if (copy.upperBound == null) {
            return validateBound(copy.lowerBound)
        }

        if (!validateBound(copy.lowerBound) || !validateBound(copy.upperBound)) return false

        if (copy.lowerBound.major == copy.upperBound.major) {
            return copy.lowerBound.minor <= copy.upperBound.minor
        }

        return copy.lowerBound.major <= copy.upperBound.major
    }

    private fun validateBound(bound: VersionBoundValue): Boolean =
        bound.major in 1..100 && bound.minor in 0..60

    data class Value(val lowerBound: VersionBoundValue, val upperBound: VersionBoundValue?)
}

data class VersionBoundValue(val major: Int, val minor: Int)
