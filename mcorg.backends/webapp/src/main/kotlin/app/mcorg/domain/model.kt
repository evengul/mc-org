package app.mcorg.domain

data class User(val id:Int, val username:String)

interface Authorized {
    val id: Int
    var name: String
}

data class World(override val id: Int, override var name: String) : Authorized

data class Team(val worldId: Int, override val id: Int, override var name: String) : Authorized

data class SlimProject(val worldId: Int,
                       val teamId: Int,
                       override val id: Int,
                       override var name: String) : Authorized

data class Project(val worldId: Int,
                   val teamId: Int,
                   override val id: Int,
                   override var name: String,
                   var archived: Boolean,
                   val tasks: MutableList<Task>) : Authorized

data class ProjectDependency(val projectId: Int, val priority: Priority)

data class Task(val id: Int, var name: String, val priority: Priority, val dependencies: MutableList<ProjectDependency>, var needed: Int, var done: Int)

data class SlimResourcePack(override val id: Int,
                            override var name: String): Authorized

data class ResourcePack(override val id: Int,
                        override var name: String,
                        val version: String,
                        val serverType: ServerType,
                        val resources: MutableList<Resource>) : Authorized

data class Resource(val id: Int, val name: String, val type: ResourceType, val downloadUrl: String)

data class UserPermissions<T : Authorized>(val userId: Int, val permissions: Map<PermissionLevel, List<Pair<T, Authority>>>)

