package app.mcorg.presentation.mappers

import app.mcorg.domain.pipeline.Result
import io.ktor.http.*

fun Parameters.optional(key: String): String? = this[key]
fun Parameters.required(key: String): String = this[key] ?: throw IllegalArgumentException("$key is required")
fun Parameters.requiredInt(key: String): Int = this[key]?.toIntOrNull() ?: throw IllegalArgumentException("$key is required")

fun <E> Parameters.required(key: String, error: E) = this[key]?.let { Result.success(it) } ?: Result.failure(error)
fun <E> Parameters.int(key: String, error: E) = this[key]?.toIntOrNull()?.let { Result.success(it) } ?: Result.failure(error)

fun Parameters.optionalBoolean(key: String): Boolean? = this[key]?.toBooleanStrictOrNull()