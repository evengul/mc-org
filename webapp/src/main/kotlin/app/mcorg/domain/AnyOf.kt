package app.mcorg.domain

data class AnyOf<T>(val values: List<T>) {
    companion object {
        fun <T> single(value: T) = AnyOf(listOf(value))
    }
}
