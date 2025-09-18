package app.mcorg.pipeline.world

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.failure.HandleGetWorldFailure
import java.sql.ResultSet

val worldQueryStep = DatabaseSteps.query<Int, HandleGetWorldFailure, World>(
    getWorldQuery,
    parameterSetter = { statement, input ->
        statement.setInt(1, input)
    },
    errorMapper = {
        when(it) {
            is DatabaseFailure.NotFound -> HandleGetWorldFailure.WorldNotFound
            else -> HandleGetWorldFailure.SystemError("A system error occurred while fetching the world.")
        }
    },
    resultMapper = { if(it.next()) it.toWorld() else throw IllegalStateException("World should exist at this point") }
)

data class WorldMemberQueryInput(val userId: Int, val worldId: Int)

val worldMemberQueryStep = DatabaseSteps.query<WorldMemberQueryInput, DatabaseFailure, WorldMember>(
    SafeSQL.select("SELECT * FROM world_members WHERE user_id = ? AND world_id = ?"),
    parameterSetter = { statement, input ->
        statement.setInt(1, input.userId)
        statement.setInt(2, input.worldId)
    },
    errorMapper = { it },
    resultMapper = { if(it.next()) it.toWorldMember() else throw IllegalStateException() }
)

val worldMembersQueryStep = DatabaseSteps.query<Int, HandleGetWorldFailure, List<WorldMember>>(
    getWorldMembersQuery,
    parameterSetter = { statement, input ->
        statement.setInt(1, input)
    },
    errorMapper = { HandleGetWorldFailure.SystemError("A system error occurred while fetching the members of the world.") },
    resultMapper = {
        buildList {
            while (it.next()) {
                add(it.toWorldMember())
            }
        }
    }
)

private fun ResultSet.toWorldMember(): WorldMember {
    return WorldMember(
        id = getInt("id"),
        worldId = getInt("world_id"),
        displayName = getString("display_name"),
        worldRole = Role.fromLevel(getInt("world_role")),
        createdAt = getTimestamp("created_at").toInstant().atZone(java.time.ZoneOffset.UTC),
        updatedAt = getTimestamp("updated_at").toInstant().atZone(java.time.ZoneOffset.UTC)
    )
}