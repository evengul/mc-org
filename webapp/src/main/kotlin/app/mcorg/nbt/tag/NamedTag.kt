package app.mcorg.nbt.tag

data class NamedTag<T>(
    val name: String,
    val tag: Tag<T>
)
