package app.mcorg.infrastructure.reader.entities.loot

import app.mcorg.domain.minecraft.model.Item
import app.mcorg.domain.minecraft.model.Loot
import app.mcorg.infrastructure.reader.serializer.ListOrStringSerializer
import app.mcorg.infrastructure.reader.serializer.PredicateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LootEntity(
    val type: String,
    val pools: List<LootPoolEntity>,
    @SerialName("random_sequence")
    val randomSequence: String
)

@Serializable
data class LootPoolEntity(
    @SerialName("bonus_rolls")
    val bonusRolls: Double,
    val rolls: Double,
    val entries: List<LootPoolEntryEntity>,
    val conditions: List<LootPoolCondition>? = null
)

@Serializable
data class LootPoolEntryEntity(
    val type: String,
    val name: String? = null,
    val children: List<LootPoolEntryEntity>? = null,
    val functions: List<LootPoolEntryFunction>? = null,
    val conditions: List<LootPoolCondition>? = null,
    val weight: Int = 0
)

@Serializable
data class LootPoolCondition(
    val condition: String,
    val enchantment: String? = null,
    val chances: List<Double>? = null,
    val term: LootPoolConditionTerm? = null,
    val terms: List<LootPoolConditionTerm>? = null,
    val block: String? = null,
    val properties: Map<String, String>? = null
)

@Serializable
data class LootPoolConditionTerm(
    val condition: String,
    val terms: List<LootPoolConditionTerm>? = null,
    val predicate: LootPoolConditionTermPredicate? = null
)

@Serializable
data class LootPoolConditionTermPredicate(
    val predicates: Map<String, List<LootPoolConditionTermPredicate>>? = null,
    val enchantments: String? = null,
    val levels: Map<String, Int>? = null,
    @Serializable(with = ListOrStringSerializer::class)
    val items: Any? = null,
)

@Serializable
data class LootPoolEntryFunction(
    val effects: List<LootPoolEntryFunctionEffect>? = null,
    val add: Boolean? = null,
    val conditions: List<LootPoolCondition>? = null,
    val count: LootPoolEntryFunctionCount? = null,
    val function: String
)

@Serializable
data class LootPoolEntryFunctionCount(
    val type: String,
    val max: Double,
    val min: Double
)

@Serializable
data class LootPoolEntryFunctionEffect(
    val type: String,
    val duration: LootPoolEntryFunctionEffectDuration
)

@Serializable
data class LootPoolEntryFunctionEffectDuration(
    val type: String,
    val max: Double,
    val min: Double,
)

fun LootEntity.toLoot(): Loot {
    return Loot(
        type,
        pools.flatMap { it.entries }.filter { it.type == "minecraft:item" && it.name != null }.map { Item(it.name!!) to 0..1 }
    )
}
