package app.mcorg.domain

interface Users {
    fun userExists(id: Int): Boolean
    fun usernameExists(username: String): Boolean
    fun emailExists(email: String): Boolean
    fun getUser(id: Int): User?
    fun getUser(username: String): User?
    fun getUserByUsernameIfPasswordMatches(username: String, password: String): User?
    fun createUser(username: String, email: String, password: String): Int
    fun searchUsers(searchTerm: String): List<User>
}

interface Permissions {
    fun getPermissions(userId: Int): UserPermissions<Authorized>
    fun getWorldPermissions(userId: Int): UserPermissions<World>
    fun getTeamPermissions(userId: Int): UserPermissions<Team>
    fun getPackPermissions(userId: Int): UserPermissions<ResourcePack>
    fun hasAnyWorldPermission(userId: Int): Boolean

    fun hasWorldPermission(userId: Int, authority: Authority, worldId: Int): Boolean
    fun hasTeamPermission(userId: Int, authority: Authority, teamId: Int): Boolean
    fun hasPackPermission(userId: Int, authority: Authority, packId: Int): Boolean

    fun addWorldPermission(userId: Int, worldId: Int, authority: Authority): Int
    fun addTeamPermission(userId: Int, teamId: Int, authority: Authority): Int
    fun addPackPermission(userId: Int, packId: Int, authority: Authority): Int

    fun changeWorldPermission(userId: Int, worldId: Int, authority: Authority)
    fun changeTeamPermission(userId: Int, teamId: Int, authority: Authority)
    fun changePackPermission(userId: Int, packId: Int, authority: Authority)

    fun removeWorldPermission(userId: Int, worldId: Int)
    fun removeTeamPermission(userId: Int, teamId: Int)
    fun removePackPermission(userId: Int, packId: Int)

    fun getUsersInTeam(teamId: Int): List<User>
    fun hasTeamPermissionInWorld(userId: Int, worldId: Int): Boolean
}

interface Worlds {
    fun getWorld(id: Int): World?
    fun deleteWorld(id: Int)
    fun createWorld(name: String): Int
    fun getUserWorlds(username: String): List<World>

    fun changeWorldName(id: Int, name: String)
}

interface Teams {
    fun getTeam(id: Int): Team?
    fun deleteTeam(id: Int)
    fun createTeam(worldId: Int, name: String): Int
    fun getWorldTeams(worldId: Int): List<Team>

    fun changeTeamName(id: Int, name: String)
}

interface Projects {
    fun getProject(id: Int, includeTasks: Boolean = false, includeDependencies: Boolean = false): Project?
    fun deleteProject(id: Int)
    fun getTeamProjects(id: Int): List<SlimProject>
    fun createProject(worldId: Int, teamId: Int, name: String): Int
    fun getUserProjects(username: String): List<Project>

    fun changeProjectName(id: Int, name: String)

    fun archiveProject(id: Int)
    fun openProject(id: Int)

    fun getTask(projectId: Int, taskId: Int): Task?
    fun addCountableTask(projectId: Int, name: String, priority: Priority, needed: Int): Int
    fun addDoableTask(projectId: Int, name: String, priority: Priority): Int
    fun removeTask(id: Int)
    fun completeTask(id: Int)
    fun undoCompleteTask(id: Int)
    fun updateCountableTask(id: Int, needed: Int, done: Int)
    fun taskRequiresMore(id: Int, needed: Int)
    fun taskDoneMore(id: Int, done: Int)

    fun addProjectDependencyToTask(taskId: Int, projectId: Int, priority: Priority): Int
    fun removeProjectDependencyToTask(dependencyId: Int)
}

interface Packs {
    fun getPack(id: Int): ResourcePack?
    fun deletePack(id: Int)
    fun createPack(name: String, version: String, serverType: ServerType): Int
    fun getWorldPacks(id: Int): List<ResourcePack>
    fun getTeamPacks(id: Int): List<ResourcePack>
    fun getUserPacks(userId: Int): List<ResourcePack>

    fun changePackName(id: Int, name: String)

    fun sharePackWithWorld(id: Int, worldId: Int)
    fun sharePackWithTeam(id: Int, teamId: Int)
    fun unSharePackWithWorld(id: Int, worldId: Int)
    fun unSharePackWithTeam(id: Int, teamId: Int)

    fun addResource(packId: Int, name: String, type: ResourceType, downloadUrl: String): Int
    fun removeResource(id: Int)

    fun upgradePack(id: Int, newVersion: String): ResourcePack
}