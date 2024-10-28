package app.mcorg.infrastructure.reader

import app.mcorg.domain.minecraft.Biome
import app.mcorg.domain.minecraft.BiomeApi

class BiomeReader : BiomeApi, NameReader("biome.minecraft.") {
    override fun getBiomes(): List<Biome> {
        return getValues()
            .map { Biome(it.first, it.second) }
            .toList()
    }
}