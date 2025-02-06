package app.mcorg.infrastructure.reader

import app.mcorg.infrastructure.reader.entities.loot.LootEntity
import kotlinx.serialization.json.Json

class LootReader {
    fun getValues(): List<LootEntity> {
        return LootCategoryReader("archaeology").getValues() +
                LootCategoryReader("blocks").getValues() +
                LootCategoryReader("chests").getValues() +
                LootCategoryReader("chests/trial_chambers").getValues() +
                LootCategoryReader("chests/village").getValues() +
                LootCategoryReader("entities").getValues() +
                LootCategoryReader("entities/sheep").getValues() +
                LootCategoryReader("equipment").getValues() +
                LootCategoryReader("gameplay").getValues() +
                LootCategoryReader("gameplay/fishing").getValues() +
                LootCategoryReader("gameplay/hero_of_the_village").getValues() +
                LootCategoryReader("pots/trial_chambers").getValues() +
                LootCategoryReader("shearing").getValues() +
                LootCategoryReader("shearing/sheep").getValues() +
                LootCategoryReader("shearing/mooshroom").getValues() +
                LootCategoryReader("spawners/ominous/trial_chamber").getValues() +
                LootCategoryReader("spawners/trial_chamber").getValues()
    }

    private class LootCategoryReader(subPath: String) : DirectoryReader<LootEntity>("minecraft/loot_table/$subPath") {
        override fun parseContent(content: String): LootEntity {
            try {
                return Json.decodeFromString(content)
            } catch (e: Exception) {
                println("Error while parsing $content")
                throw e
            }
        }
    }
}