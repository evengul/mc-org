package no.mcorg.domain

interface Users {
    fun getUser(id: Int): User?
    fun checkUserPassword(username: String, password: String): Boolean
    fun createUser(username: String, password: String)
    fun searchUsers(searchTerm: String): List<User>
}

interface Permissions {
    fun getPermission(userId: Int): UserPermissions<SimplyAuthorized>
    fun getWorldPermissions(userId: Int): UserPermissions<World>
    fun getTeamPermissions(userId: Int): UserPermissions<Team>
    fun getPackPermissions(userId: Int): UserPermissions<ResourcePack>

    fun addWorldPermission(userId: Int, worldId: Int, authority: Authority)
    fun addTeamPermission(userId: Int, teamId: Int, authority: Authority)
    fun addPackPermission(userId: Int, packId: Int, authority: Authority)

    fun changeWorldPermission(userId: Int, worldId: Int, authority: Authority)
    fun changeTeamPermission(userId: Int, teamId: Int, authority: Authority)
    fun changePackPermission(userId: Int, packId: Int, authority: Authority)

    fun removeWorldPermission(userId: Int, worldId: Int)
    fun removeTeamPermission(userId: Int, teamId: Int)
    fun removePackPermission(userId: Int, packId: Int)
}

interface Worlds {
    fun getWorld(id: Int): World?
    fun createWorld(name: String)
    fun getUserWorlds(username: String): List<World>

    fun changeWorldName(id: Int, name: String)

}

interface Teams {
    fun getTeam(id: Int): Team?
    fun createTeam(worldId: Int, name: String)
    fun getUserTeams(username: String): List<Team>

    fun changeTeamName(id: Int, name: String)
}

interface Projects {
    fun getProject(id: Int): Project?
    fun createProject(worldId: Int, teamId: Int, name: String)
    fun getUserProjects(username: String): List<Project>

    fun changeProjectName(id: Int, name: String)

    fun archiveProject(id: Int)
    fun openProject(id: Int)

    fun addCountableTask(name: String, priority: Priority, needed: Int)
    fun addDoableTask(name: String, priority: Priority)
    fun removeTask(id: Int)
    fun completeTask(id: Int)
    fun taskRequiresMore(id: Int, needed: Int)
    fun taskDoneMore(id: Int, done: Int)

    fun addProjectDependencyToTask(taskId: Int, projectId: Int, priority: Priority)
    fun removeProjectDependencyToTask(dependencyId: Int)
}

interface Packs {
    fun getPack(id: Int): ResourcePack?
    fun createPack(name: String, version: String, serverType: ServerType)
    fun getUserPacks(username: String): List<ResourcePack>

    fun changePackName(id: Int, name: String)

    fun addResource(packId: Int, name: String, type: ResourceType, downloadUrl: String)
    fun removeResource(id: Int)

    fun upgradePack(id: Int, newVersion: String): ResourcePack
}