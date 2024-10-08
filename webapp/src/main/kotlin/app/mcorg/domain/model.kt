package app.mcorg.domain


data class User(val id: Int, val username:String)

data class Profile(val id: Int,
                   val username: String,
                   val email: String,
                   val profilePhoto: String?,
                   val selectedWorld: Int?,
                   val technicalPlayer: Boolean)

data class MinecraftProfile(val username: String, val email: String)

data class World(
    val id: Int,
    val name: String,
    val gameType: GameType,
    val version: WorldVersion,
    val isTechnical: Boolean,
)

data class SlimProject(val worldId: Int,
                       val id: Int,
                       val name: String,
                       val priority: Priority,
                       val dimension: Dimension,
                       val assignee: User?,
                       val progress: Double)

data class Project(val worldId: Int,
                   val id: Int,
                   val name: String,
                   val archived: Boolean,
                   val priority: Priority,
                   val dimension: Dimension,
                   val assignee: User?,
                   val progress: Double,
                   val requiresPerimeter: Boolean,
                   val tasks: MutableList<Task>)

data class ProjectDependency(val projectId: Int, val priority: Priority)

data class PremadeTask(val name: String, val needed: Int)

data class Task(val id: Int,
                var name: String,
                val priority: Priority,
                val dependencies: MutableList<ProjectDependency>,
                var needed: Int,
                var done: Int,
                val assignee: User?,
                val taskType: TaskType,
)

data class Contraption(
    val id: Int,
    val name: String,
    val description: String,
    val archived: Boolean,
    val authors: List<String>,
    val worksInGame: GameType,
    val worksInVersion: ContraptionVersion,
    val schematicUrl: String?,
    val worldDownloadUrl: String?,
)

data class WorldVersion(val major: Int, val minor: Int)
data class ContraptionVersion(val lowerBound: WorldVersion, val upperBound: WorldVersion?)

data class UserPermissions(val userId: Int, val ownedWorlds: List<World>, val participantWorlds: List<World>)

