package app.mcorg.domain.api

import app.mcorg.domain.model.worlds.World

interface Worlds {
    fun getWorld(id: Int): World?
    fun worldExists(id: Int): Boolean
    fun worldExistsByName(name: String): Boolean
    fun deleteWorld(id: Int)
    fun createWorld(name: String): Int

    fun changeWorldName(id: Int, name: String)
}