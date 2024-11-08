package app.mcorg.domain.minecraft

import app.mcorg.domain.minecraft.model.Biome

interface BiomeApi {
    fun getBiomes(): List<Biome>
}