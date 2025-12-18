package app.mcorg.domain.model.idea.schema

import kotlinx.serialization.Serializable

@Serializable
sealed interface CategoryValue {
    fun display(): String = toString()

    @Serializable
    data class MapValue(val value: Map<String, CategoryValue>) : CategoryValue {
        override fun display(): String {
            return value.entries.joinToString(", ") { (key, v) -> "$key: ${v.display()}" }
        }
    }

    @Serializable
    data class BooleanValue(val value: Boolean) : CategoryValue {
        override fun toString(): String = value.toString()

        override fun display(): String {
            return if (value) "Yes" else "No"
        }
    }

    @Serializable
    data class TextValue(val value: String) : CategoryValue {
        override fun toString(): String = value
    }

    @Serializable
    data class IntValue(val value: Int) : CategoryValue {
        override fun toString(): String = value.toString()
    }

    @Serializable
    data class MultiSelectValue(val values: Set<String>) : CategoryValue {
        override fun display(): String {
            return values.joinToString(", ")
        }
    }

    data object IgnoredValue : CategoryValue
}