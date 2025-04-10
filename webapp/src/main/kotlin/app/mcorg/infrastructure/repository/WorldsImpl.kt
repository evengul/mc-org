package app.mcorg.infrastructure.repository

import app.mcorg.domain.api.Worlds

class WorldsImpl : Worlds, Repository() {
    override fun worldExists(id: Int): Boolean {
        getConnection().use {
            it.prepareStatement("select 1 from world where id = ? limit 1")
                .apply { setInt(1, id) }
                .executeQuery()
                .apply {
                    return next()
                }
        }
    }
}