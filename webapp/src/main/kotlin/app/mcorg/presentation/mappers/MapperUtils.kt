package app.mcorg.presentation.mappers

import io.ktor.http.*

fun Parameters.optional(key: String): String? = this[key]
fun Parameters.required(key: String): String = this[key] ?: throw IllegalArgumentException("$key is required")
fun Parameters.requiredInt(key: String): Int = this[key]?.toIntOrNull() ?: throw IllegalArgumentException("$key is required")

fun Parameters.optionalBoolean(key: String): Boolean? = this[key]?.toBooleanStrictOrNull()