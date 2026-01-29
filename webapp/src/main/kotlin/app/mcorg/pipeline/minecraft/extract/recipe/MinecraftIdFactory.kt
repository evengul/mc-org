package app.mcorg.pipeline.minecraft.extract.recipe

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag

object MinecraftIdFactory {
    fun minecraftIdFromId(id: String): MinecraftId {
        return if (id.startsWith("#")) {
            MinecraftTag(
                id = id,
                name = "",
                content = emptyList()
            )
        } else {
            Item(
                id = id,
                name = ""
            )
        }
    }
}