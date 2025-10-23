package app.mcorg.pipeline.admin

import app.mcorg.domain.model.admin.ManagedUser
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import java.time.ZoneOffset

data class GetManagedUsersInput(
    val query: String = "",
    val page: Int = 1,
    val pageSize: Int = 10
)

object GetManagedUsersStep : Step<GetManagedUsersInput, HandleGetAdminPageFailures, List<ManagedUser>> {
    override suspend fun process(input: GetManagedUsersInput): Result<HandleGetAdminPageFailures, List<ManagedUser>> {
        return DatabaseSteps.query<GetManagedUsersInput, HandleGetAdminPageFailures, List<ManagedUser>>(
            SafeSQL.select("""
                SELECT users.id as id, minecraft_profiles.username as minecraft_username, users.email, minecraft_profiles.created_at as joined_at, minecraft_profiles.last_login as last_seen
                FROM users
                JOIN minecraft_profiles on users.id = minecraft_profiles.user_id
                WHERE ? = '' OR LOWER(minecraft_profiles.username) LIKE '%' || ? || '%'
                ORDER BY minecraft_profiles.username DESC 
                LIMIT ? OFFSET ?
            """.trimIndent()),
            parameterSetter = { statement, (query, page, pageSize) ->
                val normalizedQuery = query.trim().lowercase()
                val offset = (page - 1) * pageSize

                statement.setString(1, normalizedQuery)
                statement.setString(2, normalizedQuery)
                statement.setInt(3, pageSize)
                statement.setInt(4, offset)
            },
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