package app.mcorg.domain.model.minecraft

data class ServerData(
    val version: MinecraftVersion.Release,
    val items: List<Item>
)

data class Item(
    val id: String,
    val name: String
)