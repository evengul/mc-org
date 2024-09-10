package app.mcorg.domain

interface Users {
    fun userExists(id: Int): Boolean
    fun usernameExists(username: String): Boolean
    fun emailExists(email: String): Boolean
    fun getUser(id: Int): User?
    fun getUser(username: String): User?
    fun getUserByUsernameIfPasswordMatches(username: String, password: String): User?
    fun createUser(username: String, email: String): Int
    fun deleteUser(id: Int)
    fun searchUsers(searchTerm: String): List<User>
    fun getProfile(id: Int): Profile?
    fun selectWorld(userId: Int, worldId: Int)
    fun unSelectWorldForAll(worldId: Int)
    fun isTechnical(id: Int)
    fun isNotTechnical(id: Int)
}

interface Permissions {
    fun getPermissions(userId: Int): UserPermissions
    fun hasAnyWorldPermission(userId: Int): Boolean
    fun hasWorldPermission(userId: Int, authority: Authority, worldId: Int): Boolean
    fun addWorldPermission(userId: Int, worldId: Int, authority: Authority): Int
    fun changeWorldPermission(userId: Int, worldId: Int, authority: Authority)
    fun removeWorldPermission(userId: Int, worldId: Int)
    fun removeWorldPermissionForAll(worldId: Int)
    fun getUsersInWorld(worldId: Int): List<User>
    fun removeUserPermissions(userId: Int)
}

interface Worlds {
    fun getWorld(id: Int): World?
    fun deleteWorld(id: Int)
    fun createWorld(name: String): Int

    fun changeWorldName(id: Int, name: String)
}

interface Projects {
    fun getProject(id: Int, includeTasks: Boolean = false, includeDependencies: Boolean = false): Project?
    fun deleteProject(id: Int)
    fun getWorldProjects(id: Int): List<SlimProject>
    fun createProject(worldId: Int, name: String, dimension: Dimension, priority: Priority, requiresPerimeter: Boolean): Int

    fun changeProjectName(id: Int, name: String)

    fun archiveProject(id: Int)
    fun openProject(id: Int)

    fun getProjectAssignee(id: Int): User?
    fun assignProject(id: Int, userId: Int)
    fun removeProjectAssignment(id: Int)

    fun addCountableTask(projectId: Int, name: String, priority: Priority, needed: Int): Int
    fun addDoableTask(projectId: Int, name: String, priority: Priority): Int
    fun removeTask(id: Int)
    fun completeTask(id: Int)
    fun undoCompleteTask(id: Int)
    fun updateCountableTask(id: Int, needed: Int, done: Int)
    fun taskRequiresMore(id: Int, needed: Int)
    fun taskDoneMore(id: Int, doneMore: Int)
    fun editTaskRequirements(id: Int, needed: Int, done: Int)
    fun getTaskAssignee(id: Int): User?
    fun assignTask(id: Int, userId: Int)
    fun removeTaskAssignment(id: Int)
    fun removeUserAssignments(id: Int)

    fun addProjectDependencyToTask(taskId: Int, projectId: Int, priority: Priority): Int
    fun removeProjectDependencyToTask(dependencyId: Int)
}

interface Minecraft {
    suspend fun getProfile(authorizationCode: String, clientId: String, clientSecret: String): MinecraftProfile
}