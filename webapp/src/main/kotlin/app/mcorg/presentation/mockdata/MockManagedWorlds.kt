package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.admin.ManagedWorld

object MockManagedWorlds {
    fun getManagedWorlds() = MockWorlds.getCompleteList()
        .map {
            ManagedWorld(
                id = it.id,
                name = it.name,
                version = it.version,
                projects = it.totalProjects,
                members = (Math.random() * 1000.0).toInt(),
                createdAt = it.createdAt,
            )
        }
}