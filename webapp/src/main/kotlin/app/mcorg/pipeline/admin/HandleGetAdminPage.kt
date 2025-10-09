package app.mcorg.pipeline.admin

import app.mcorg.domain.model.admin.ManagedUser
import app.mcorg.domain.model.admin.ManagedWorld
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.notification.GetUnreadNotificationCountStep
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.admin.adminPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import java.time.ZoneOffset
import kotlin.collections.buildList

interface HandleGetAdminPageFailures {
    object DatabaseError : HandleGetAdminPageFailures
}

private object GetManagedUsersStep : Step<Int, HandleGetAdminPageFailures, List<ManagedUser>> {
    override suspend fun process(input: Int): Result<HandleGetAdminPageFailures, List<ManagedUser>> {
        return DatabaseSteps.query<Int, HandleGetAdminPageFailures, List<ManagedUser>>(
            SafeSQL.select("""
                SELECT users.id as id, minecraft_profiles.username as minecraft_username, users.email, minecraft_profiles.created_at as joined_at, minecraft_profiles.last_login as last_seen
                FROM users
                JOIN minecraft_profiles on users.id = minecraft_profiles.user_id
            """.trimIndent()),
            errorMapper = { HandleGetAdminPageFailures.DatabaseError },
            resultMapper = { rs -> buildList {
                while(rs.next()) {
                    add(ManagedUser(
                        id = rs.getInt("id"),
                        displayName = rs.getString("minecraft_username"),
                        minecraftUsername = rs.getString("minecraft_username"),
                        email = rs.getString("email"),
                        globalRole = Role.MEMBER, // TODO: replace with list of global roles,
                        joinedAt = rs.getTimestamp("joined_at").toInstant().atZone(ZoneOffset.UTC),
                        lastSeen = rs.getTimestamp("last_seen").toInstant().atZone(ZoneOffset.UTC),
                    ))
                }
            }}
        ).process(input)
    }
}

private object GetManagedWorldsStep : Step<Int, HandleGetAdminPageFailures, List<ManagedWorld>> {
    override suspend fun process(input: Int): Result<HandleGetAdminPageFailures, List<ManagedWorld>> {
        return DatabaseSteps.query<Int, HandleGetAdminPageFailures, List<ManagedWorld>>(
            SafeSQL.select("""
                SELECT world.id, 
                world.name, 
                world.version, 
                (SELECT COUNT(*) FROM projects WHERE projects.world_id = world.id) as total_projects, 
                (SELECT COUNT(*) FROM world_members WHERE world_members.world_id = world.id) as total_members, 
                world.created_at
                FROM world
                ORDER BY world.id
            """.trimIndent()),
            errorMapper = { HandleGetAdminPageFailures.DatabaseError },
            resultMapper = { rs -> buildList {
                while(rs.next()) {
                    add(ManagedWorld(
                        id = rs.getInt("id"),
                        name = rs.getString("name"),
                        version = MinecraftVersion.fromString(rs.getString("version")),
                        projects = rs.getInt("total_projects"),
                        members = rs.getInt("total_members"),
                        createdAt = rs.getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC)
                    ))
                }
            }}
        ).process(input)
    }
}

suspend fun ApplicationCall.handleGetAdminPage() {
    val user = getUser()

    val userPipe = Pipeline.create<HandleGetAdminPageFailures, Int>()
        .pipe(GetManagedUsersStep)

    val worldsPipe = Pipeline.create<HandleGetAdminPageFailures,  Int>()
        .pipe(GetManagedWorldsStep)

    val notificationsPipe = Pipeline.create<HandleGetAdminPageFailures, Int>()
        .pipe(object : Step<Int, HandleGetAdminPageFailures, Int> {
            override suspend fun process(input: Int): Result<HandleGetAdminPageFailures, Int> {
                return when (val result = GetUnreadNotificationCountStep.process(input)) {
                    is Result.Success -> Result.success(result.value)
                    is Result.Failure -> Result.success(0)
                }
            }
        })

    executeParallelPipeline(
        onSuccess = { (users, worlds, notifications) -> respondHtml(adminPage(user, users, worlds, notifications))},
        onFailure = { respondHtml("A system error occurred.") }
    ) {
        val users = pipeline("users", user.id, userPipe)
        val worlds = pipeline("worlds", user.id, worldsPipe)
        val notifications = pipeline("notifications", user.id, notificationsPipe)
        merge("data", users, worlds, notifications) { u, w, n ->
            Result.success(Triple(u, w, n))
        }
    }
}