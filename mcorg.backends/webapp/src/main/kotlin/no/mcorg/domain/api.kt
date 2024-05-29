package no.mcorg.domain

interface Users {
    fun userExists(id: Int): Boolean
    fun getUser(id: Int): User?
    fun checkUserPassword(username: String, password: String): Boolean
    fun createUser(username: String, password: String): Int
    fun searchUsers(searchTerm: String): List<User>
}

interface Permissions {
    fun getPermissions(userId: Int): UserPermissions<Authorized>
    fun getWorldPermissions(userId: Int): UserPermissions<World>
    fun getTeamPermissions(userId: Int): UserPermissions<Team>
    fun getPackPermissions(userId: Int): UserPermissions<ResourcePack>
    fun hasWorldPermission(userId: Int): Boolean

    fun addWorldPermission(userId: Int, worldId: Int, authority: Authority): Int
    fun addTeamPermission(userId: Int, teamId: Int, authority: Authority): Int
    fun addPackPermission(userId: Int, packId: Int, authority: Authority): Int

    fun changeWorldPermission(userId: Int, worldId: Int, authority: Authority)
    fun changeTeamPermission(userId: Int, teamId: Int, authority: Authority)
    fun changePackPermission(userId: Int, packId: Int, authority: Authority)

    fun removeWorldPermission(userId: Int, worldId: Int)
    fun removeTeamPermission(userId: Int, teamId: Int)
    fun removePackPermission(userId: Int, packId: Int)
}

interface Worlds {
    fun getWorld(id: Int): World?
    fun createWorld(name: String): Int
    fun getUserWorlds(username: String): List<World>

    fun changeWorldName(id: Int, name: String)
}

interface Teams {
    fun getTeam(id: Int): Team?
    fun createTeam(worldId: Int, name: String): Int
    fun getWorldTeams(worldId: Int): List<Team>

    fun changeTeamName(id: Int, name: String)
}

interface Projects {
    fun getProject(id: Int, includeTasks: Boolean = false, includeDependencies: Boolean = false): Project?
    fun getTeamProjects(id: Int): List<SlimProject>
    fun createProject(worldId: Int, teamId: Int, name: String): Int
    fun getUserProjects(username: String): List<Project>

    fun changeProjectName(id: Int, name: String)

    fun archiveProject(id: Int)
    fun openProject(id: Int)

    fun addCountableTask(projectId: Int, name: String, priority: Priority, needed: Int): Int
    fun addDoableTask(projectId: Int, name: String, priority: Priority): Int
    fun removeTask(id: Int)
    fun completeTask(id: Int)
    fun taskRequiresMore(id: Int, needed: Int)
    fun taskDoneMore(id: Int, done: Int)

    fun addProjectDependencyToTask(taskId: Int, projectId: Int, priority: Priority): Int
    fun removeProjectDependencyToTask(dependencyId: Int)
}

interface Packs {
    fun getPack(id: Int): ResourcePack?
    fun createPack(name: String, version: String, serverType: ServerType): Int
    fun getWorldPacks(id: Int): List<ResourcePack>
    fun getTeamPacks(id: Int): List<ResourcePack>
    fun getUserPacks(userId: Int): List<ResourcePack>

    fun changePackName(id: Int, name: String)

    fun addResource(packId: Int, name: String, type: ResourceType, downloadUrl: String): Int
    fun removeResource(id: Int)

    fun upgradePack(id: Int, newVersion: String): ResourcePack
}