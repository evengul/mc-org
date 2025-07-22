package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import java.time.ZonedDateTime

object MockWorlds {
    val survivalWorld = World(
        id = 1,
        name = "Survival World",
        description = "My main survival world",
        version = MinecraftVersion.release(20, 0),
        totalProjects = 4,
        completedProjects = 2,
        updatedAt = ZonedDateTime.now(),
        createdAt = mockZonedDateTime(2023, 1, 1)
    )

    val creativeWorld = World(
        id = 2,
        name = "Creative World",
        description = "Collaborative creative building server",
        version = MinecraftVersion.release(20, 1),
        totalProjects = 0,
        completedProjects = 0,
        updatedAt = ZonedDateTime.now(),
        createdAt = mockZonedDateTime(2023, 2, 15)
    )

    val hardcoreWorld = World(
        id = 3,
        name = "Hardcore Challenge",
        description = "Hardcore survival challenge world",
        version = MinecraftVersion.release(19, 4),
        totalProjects = 1,
        completedProjects = 1,
        updatedAt = ZonedDateTime.now(),
        createdAt = mockZonedDateTime(2023, 3, 10)
    )

    val fantasyRealm = World(
        id = 4,
        name = "Fantasy Realm",
        description = "A magical world full of adventures",
        version = MinecraftVersion.release(21, 0),
        totalProjects = 5,
        completedProjects = 3,
        updatedAt = ZonedDateTime.now(),
        createdAt = mockZonedDateTime(2023, 4, 20)
    )

    private val worlds = mutableListOf(
        survivalWorld,
        creativeWorld,
        hardcoreWorld
    )

    fun getList() = worlds.toList()

    fun getById(id: Int) = worlds.find { it.id == id }

    fun add(world: World) = worlds.add(world)
}