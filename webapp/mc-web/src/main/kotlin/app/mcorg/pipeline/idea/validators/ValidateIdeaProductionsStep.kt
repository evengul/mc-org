package app.mcorg.pipeline.idea.validators

import app.mcorg.domain.model.idea.IdeaProductionMode
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import io.ktor.http.Parameters

/**
 * Validates the PRODUCTIONS stage of the idea form (MCO-412).
 *
 * Two things are rejected here, and both were previously silent:
 *
 * 1. **Two modes resolving to the same name.** `idea_production_modes` carries
 *    `UNIQUE (idea_id, name)`, and a blank name is stored as
 *    [IdeaProductionMode.DEFAULT_MODE_NAME] — so two unnamed modes collide just as surely as two
 *    called "Max speed". Reaching the insert turns that into a 23505 and a rolled-back publish with
 *    no field-level message, which reads as "the site is broken" rather than "name the second one".
 *
 * 2. **An item id that is not in the catalog.** The item field is free text and the client prepends
 *    `minecraft:` to anything without a colon, so "Blue Ice" becomes `minecraft:Blue Ice` and stores
 *    happily. Nothing downstream forgives it: MCO-294 matches farm supply by item id, so the row
 *    never matches anything, and `ImportIdeaPipeline` fails the *entire* import of that idea into
 *    every world. Catching it here is late — after the whole form is filled — but it names the id,
 *    which is the part that was missing. The proper fix is a per-mode item search; see MCO-417.
 *
 * The version range comes from the same submitted parameters, matching what the retired
 * category-data validation did: the single-page form (MCO-310) posts every stage at once.
 */
data class ValidateIdeaProductionsStep(
    /**
     * The ids the catalog has for a version range, or null when it cannot be read. Injected so the
     * name checks stay testable without a database; production uses [catalogItemIds].
     */
    private val knownItemIds: suspend (MinecraftVersionRange) -> Set<String>? = ::catalogItemIds,
) : Step<Parameters, AppFailure, List<ValidationFailure>> {

    override suspend fun process(input: Parameters): Result<AppFailure, List<ValidationFailure>> {
        val modes = parseModes(input)
        if (modes.isEmpty()) return Result.success(emptyList())

        return Result.success(duplicateNameErrors(modes) + unknownItemErrors(input, modes))
    }

    /** Mode index -> (submitted name, item ids). Mirrors `buildStageJson`'s parameter shape. */
    private fun parseModes(params: Parameters): Map<String, Pair<String, List<String>>> {
        val names = params.names()
            .filter { it.startsWith("productionMode[") && it.endsWith("][name]") }
            .associate { key ->
                key.removePrefix("productionMode[").removeSuffix("][name]") to (params[key] ?: "")
            }
        val itemsByMode = params.names()
            .filter { it.startsWith("productionRate[") }
            .mapNotNull { key ->
                val body = key.removePrefix("productionRate[")
                val index = body.substringBefore("]", missingDelimiterValue = "")
                val itemId = body.substringAfter("][", missingDelimiterValue = "").removeSuffix("]")
                if (index.isBlank() || itemId.isBlank()) null else index to itemId
            }
            .groupBy({ it.first }, { it.second })

        // A build-time variant survives the save on its material list alone, with no rates at all
        // (MCO-463) — so it can collide on name, and leaving it out here would let two variants
        // both called "4 modules" reach the unique index and roll back the publish.
        //
        // Its material ids are deliberately *not* added to the item check: they come from parsing a
        // .litematic against the catalog, not from a text field someone typed, and an id that is in
        // the file but not in this version range is MCO-305's warning to make at import, not a
        // reason to refuse to save the design.
        val requirementModes = params.names()
            .filter { it.startsWith("modeRequirements[") }
            .mapNotNull { key ->
                key.removePrefix("modeRequirements[").substringBefore("]", missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() }
            }
            .toSet()

        return (itemsByMode.keys + requirementModes).associateWith { index ->
            (names[index] ?: "") to itemsByMode[index].orEmpty()
        }
    }

    private fun duplicateNameErrors(modes: Map<String, Pair<String, List<String>>>): List<ValidationFailure> {
        val resolved = modes.mapValues { (_, mode) -> mode.first.trim().ifBlank { IdeaProductionMode.DEFAULT_MODE_NAME } }
        return resolved.values
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .map { name ->
                val index = resolved.entries.first { it.value == name }.key
                val message = if (name == IdeaProductionMode.DEFAULT_MODE_NAME) {
                    "Give each way of running this a name — more than one is unnamed, and they cannot all be the default."
                } else {
                    "Two ways of running this are both called \"$name\". Names have to differ."
                }
                ValidationFailure.CustomValidation("productionMode[$index][name]", message)
            }
    }

    private suspend fun unknownItemErrors(
        params: Parameters,
        modes: Map<String, Pair<String, List<String>>>,
    ): List<ValidationFailure> {
        val submitted = modes.values.flatMap { it.second }.distinct()
        if (submitted.isEmpty()) return emptyList()

        val versionRange = ValidateIdeaMinecraftVersionStep.process(params).getOrNull()
            ?: MinecraftVersionRange.Unbounded
        // A catalog we cannot read is not evidence the ids are wrong — stay silent rather than
        // rejecting a form that is fine.
        val known = knownItemIds(versionRange) ?: return emptyList()

        return submitted.filterNot { it in known }.map { itemId ->
            val index = modes.entries.first { itemId in it.value.second }.key
            ValidationFailure.CustomValidation(
                "productionRate[$index][$itemId]",
                "\"$itemId\" is not an item in this version. Use the exact id, like minecraft:blue_ice.",
            )
        }
    }
}

/** The real catalog lookup, kept out of the step so tests can substitute a set of ids. */
private suspend fun catalogItemIds(versionRange: MinecraftVersionRange): Set<String>? =
    GetItemsInVersionRangeStep.process(versionRange).getOrNull()?.mapTo(mutableSetOf()) { it.id }
