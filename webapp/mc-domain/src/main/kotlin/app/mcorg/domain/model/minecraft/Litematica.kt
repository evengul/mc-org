package app.mcorg.domain.model.minecraft

data class Litematica(
    val name: String,
    val description: String,
    val author: String,
    val size: Triple<Int, Int, Int>,
    val items: Map<String, Int>
)