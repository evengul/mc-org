package app.mcorg.domain.worlds

interface Worlds {
    fun getWorld(id: Int): World?
    fun deleteWorld(id: Int)
    fun createWorld(name: String): Int

    fun changeWorldName(id: Int, name: String)
}