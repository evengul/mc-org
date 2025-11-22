package app.mcorg.domain.model.minecraft

data class ServerData(
    val version: MinecraftVersion.Release,
    val items: List<Item>
)

