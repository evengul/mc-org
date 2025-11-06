package app.mcorg.pipeline.world.extractors

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.WorldMember
import java.sql.ResultSet
import java.time.ZoneOffset

fun ResultSet.toWorldMembers(): List<WorldMember> {
    return buildList {
        while (next()) {
            add(toWorldMember())
        }
    }
}

fun ResultSet.toWorldMember(): WorldMember {
    return WorldMember(
        id = getInt("user_id"),
        worldId = getInt("world_id"),
        displayName = getString("display_name"),
        worldRole = Role.fromLevel(getInt("world_role")),
        createdAt = getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC),
        updatedAt = getTimestamp("updated_at").toInstant().atZone(ZoneOffset.UTC)
    )
}