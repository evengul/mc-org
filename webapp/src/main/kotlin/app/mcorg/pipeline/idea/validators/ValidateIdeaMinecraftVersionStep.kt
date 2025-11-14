package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ValidationFailure
import io.ktor.http.*

object ValidateIdeaMinecraftVersionStep : Step<Parameters, List<ValidationFailure>, MinecraftVersionRange> {
    override suspend fun process(input: Parameters): Result<List<ValidationFailure>, MinecraftVersionRange> {
        val rangeType = input["versionRangeType"] ?: "lowerBounded"

        when(rangeType) {
            "bounded" -> {
                val lowerBoundResult = validateLowerBound(input)
                val upperBoundResult = validateUpperBound(input)
                if (lowerBoundResult is Result.Failure || upperBoundResult is Result.Failure) {
                    val failures = mutableListOf<ValidationFailure>()
                    if (lowerBoundResult is Result.Failure) {
                        failures.add(lowerBoundResult.error)
                    }
                    if (upperBoundResult is Result.Failure) {
                        failures.add(upperBoundResult.error)
                    }
                    return Result.Failure(failures)
                }
                return Result.Success(MinecraftVersionRange.Bounded(lowerBoundResult.getOrNull()!!, upperBoundResult.getOrNull()!!))
            }
            "unbounded" -> {
                return Result.Success(MinecraftVersionRange.Unbounded)
            }
            "lowerBounded" -> {
                val lowerBoundResult = validateLowerBound(input)
                if (lowerBoundResult is Result.Failure) {
                    return Result.Failure(listOf(lowerBoundResult.error))
                }
                return Result.Success(MinecraftVersionRange.LowerBounded(lowerBoundResult.getOrNull()!!))
            }
            "upperBounded" -> {
                val upperBoundResult = validateUpperBound(input)
                if (upperBoundResult is Result.Failure) {
                    return Result.Failure(listOf(upperBoundResult.error))
                }
                return Result.Success(MinecraftVersionRange.UpperBounded(upperBoundResult.getOrNull()!!))
            }
            else -> {
                return Result.Failure(listOf(ValidationFailure.InvalidValue("versionRangeType", listOf("lowerBounded", "upperBounded", "bounded", "unbounded"))))
            }
        }
    }

    private fun validateUpperBound(parameters: Parameters): Result<ValidationFailure, MinecraftVersion> {
        val upperBound = parameters["versionTo"]?.trim()
        if (upperBound.isNullOrBlank()) {
            return Result.Failure(ValidationFailure.MissingParameter("versionTo"))
        }

        return validateMinecraftVersion(upperBound, "versionTo")
    }

    private fun validateLowerBound(parameters: Parameters): Result<ValidationFailure, MinecraftVersion> {
        val lowerBound = parameters["versionFrom"]?.trim()
        if (lowerBound.isNullOrBlank()) {
            return Result.Failure(ValidationFailure.MissingParameter("versionFrom"))
        }

        return validateMinecraftVersion(lowerBound, "versionFrom")
    }

    private fun validateMinecraftVersion(version: String, parameterName: String): Result<ValidationFailure, MinecraftVersion> {
        return try {
            Result.Success(MinecraftVersion.fromString(version))
        } catch (_: IllegalArgumentException) {
            Result.Failure(ValidationFailure.InvalidFormat(parameterName, "Expected format: x.y.z where x, y, z are integers"))
        }
    }
}