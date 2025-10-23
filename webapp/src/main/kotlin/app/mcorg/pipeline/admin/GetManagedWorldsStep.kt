package app.mcorg.pipeline.admin

import app.mcorg.domain.model.admin.ManagedWorld
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import java.time.ZoneOffset

data class GetManagedWorldsInput(
    val query: String = ""
)

object GetManagedWorldsStep : Step<GetManagedWorldsInput, HandleGetAdminPageFailures, List<ManagedWorld>> {
    override suspend fun process(input: GetManagedWorldsInput): Result<HandleGetAdminPageFailures, List<ManagedWorld>> {
        return DatabaseSteps.query<GetManagedWorldsInput, HandleGetAdminPageFailures, List<ManagedWorld>>(
            SafeSQL.select("""
                SELECT world.id, 
                world.name, 
                world.version, 
                (SELECT COUNT(*) FROM projects WHERE projects.world_id = world.id) as total_projects, 
                (SELECT COUNT(*) FROM world_members WHERE world_members.world_id = world.id) as total_members, 
                world.created_at
                FROM world WHERE (? = '' OR lower(world.name) LIKE '%' || ? || '%')
                ORDER BY world.id
            """.trimIndent()),
            parameterSetter = { statement, (query) ->
                val normalizedQuery = query.trim().lowercase()
                statement.setString(1, normalizedQuery)
                statement.setString(2, normalizedQuery)
            },
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