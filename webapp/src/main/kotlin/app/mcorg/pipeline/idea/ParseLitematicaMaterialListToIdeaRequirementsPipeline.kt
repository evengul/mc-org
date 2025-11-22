package app.mcorg.pipeline.idea

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaMinecraftVersionStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.idea.createwizard.hiddenItemRequirementField
import app.mcorg.presentation.templated.idea.createwizard.itemRequirementListEntry
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import kotlinx.html.ul
import org.slf4j.LoggerFactory

suspend fun ApplicationCall.handleParseLitematicaMaterialListToIdeaRequirements() {
    val input = receiveMultipart()

    executePipeline(
        onSuccess = { respondHtml(createHTML().ul {
            it.entries.sortedBy { (_, amount) -> amount }.map { (item, amount) ->
                li {
                    hxOutOfBands("afterbegin:#idea-item-requirements-list")
                    itemRequirementListEntry(item, amount)
                }
                div {
                    hxOutOfBands("afterbegin:#hidden-item-requirements-fields")
                    input {
                        hiddenItemRequirementField(item.id, amount)
                    }
                }
            }.joinToString { "\n" }
        }) }
    ) {
        value(input)
            .step(ValidateInputStep)
            .step(object : Step<Pair<List<String>, Parameters>, AppFailure, Pair<List<String>, MinecraftVersionRange>> {
                override suspend fun process(input: Pair<List<String>, Parameters>): Result<AppFailure, Pair<List<String>, MinecraftVersionRange>> {
                    return ValidateIdeaMinecraftVersionStep.process(input.second)
                        .mapError { AppFailure.ValidationError(it) }
                        .map { input.first to it }
                }
            })
            .step(object : Step<Pair<List<String>, MinecraftVersionRange>, AppFailure, Pair<List<String>, List<Item>>> {
                override suspend fun process(input: Pair<List<String>, MinecraftVersionRange>): Result<AppFailure, Pair<List<String>, List<Item>>> {
                    return GetItemsInVersionRangeStep.process(input.second)
                        .map { input.first to it }
                }
            })
            .step(ValidateLitematicaFileStepInput)
    }
}

private object ValidateInputStep : Step<MultiPartData, AppFailure.ValidationError, Pair<List<String>, Parameters>> {
    override suspend fun process(input: MultiPartData): Result<AppFailure.ValidationError, Pair<List<String>, Parameters>> {
        var lines: List<String>? = null
        val parameterBuilder = ParametersBuilder()

        var value: PartData? = input.readPart()

        while (value != null) {
            when (value) {
                is PartData.FileItem -> {
                    if (value.originalFileName?.endsWith(".txt") == true) {
                        lines = value.provider()
                            .readRemaining()
                            .readText()
                            .split('\n')
                            .map { it.replace("\\S", "") }
                    }
                }
                is PartData.FormItem -> {
                    value.name?.let {
                        parameterBuilder.append(it, value.value)
                    }
                }
                else -> {}
            }
            value = input.readPart()
        }

        return if (lines == null) {
            Result.failure(AppFailure.ValidationError(listOf(
                ValidationFailure.MissingParameter("file")
            )))
        } else {
            Result.success(lines to parameterBuilder.build())
        }
    }
}

private object ValidateLitematicaFileStepInput : Step<Pair<List<String>, List<Item>>, AppFailure.ValidationError, Map<Item, Int>> {
    private val logger = LoggerFactory.getLogger(ValidateLitematicaFileStepInput::class.java)

    override suspend fun process(input: Pair<List<String>, List<Item>>): Result<AppFailure.ValidationError, Map<Item, Int>> {
        val (lines, itemsInVersionRange) = input

        val itemCounts = mutableMapOf<Item, Int>()
        val errors = mutableListOf<ValidationFailure>()

        lines.forEach { line ->
            if (shouldIgnoreLine(line)) return@forEach

            val content = try {
                splitLineIntoColumns(line)
            } catch (e: Exception) {
                logger.error("Error splitting line into columns: $line", e)
                errors.add(ValidationFailure.CustomValidation("file", "Litematica file has an invalid format."))
                return@forEach
            }

            val (itemName, amountString) = content
            when (val itemValidation = validateItem(itemName, itemsInVersionRange)) {
                is Result.Success -> {
                    val item = itemValidation.value
                    when (val amountValidation = validateAmount(amountString)) {
                        is Result.Success -> {
                            val amount = amountValidation.value
                            itemCounts[item] = itemCounts.getOrDefault(item, 0) + amount
                        }
                        is Result.Failure -> {
                            errors.add(amountValidation.error)
                        }
                    }
                }
                is Result.Failure -> {
                    errors.add(itemValidation.error)
                }
            }
        }

        return if (errors.isNotEmpty()) {
            Result.failure(AppFailure.ValidationError(errors))
        } else {
            Result.success(itemCounts)
        }
    }

    private fun shouldIgnoreLine(line: String): Boolean {
        val trimmedLine = line.trim()

        return trimmedLine.startsWith("+") ||
                trimmedLine.contains("Material List for ") ||
                (trimmedLine.contains("Item") && trimmedLine.contains("Total") && trimmedLine.contains("Missing") && trimmedLine.contains("Available")) ||
                trimmedLine.isEmpty()
    }

    private fun splitLineIntoColumns(line: String): Pair<String, String> {
        val columns = line.trim()
            .removePrefix("|")
            .removeSuffix("|")
            .split("|")
            .map { it.trim() }

        return columns[0] to columns[1]
    }

    private fun validateItem(itemName: String, itemsInVersionRange: List<Item>): Result<ValidationFailure, Item> {
        val matchedItem = itemsInVersionRange.find { it.name.equals(itemName, ignoreCase = true) }

        return if (matchedItem != null) {
            Result.success(matchedItem)
        } else {
            Result.failure(ValidationFailure.InvalidValue("file", itemsInVersionRange.map { it.name }))
        }
    }

    private fun validateAmount(amount: String): Result<ValidationFailure, Int> {
        val parsedAmount = amount.toIntOrNull()

        return if (parsedAmount == null) {
            Result.failure(ValidationFailure.InvalidFormat("file", "Amount is not a valid integer."))
        } else if (parsedAmount < 0) {
            Result.failure(ValidationFailure.OutOfRange("file", 1))
        } else {
            Result.success(parsedAmount)
        }
    }
}