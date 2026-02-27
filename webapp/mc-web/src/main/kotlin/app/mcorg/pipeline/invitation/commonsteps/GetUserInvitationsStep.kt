package app.mcorg.pipeline.invitation.commonsteps

import app.mcorg.domain.model.invite.Invite
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.invitation.extractors.toInvite

val GetUserInvitationsStep = DatabaseSteps.query<Int, List<Invite>>(
    sql = SafeSQL.select("""
                SELECT 
                    i.id,
                    i.world_id,
                    w.name as world_name,
                    i.from_user_id,
                    mp_from.username as from_username,
                    i.to_user_id,
                    mp_to.username as to_username,
                    i.role,
                    i.created_at,
                    i.status,
                    i.status_reached_at
                FROM invites i
                INNER JOIN world w ON i.world_id = w.id
                INNER JOIN users u_from ON i.from_user_id = u_from.id
                INNER JOIN users u_to ON i.to_user_id = u_to.id
                LEFT JOIN minecraft_profiles mp_from ON i.from_user_id = mp_from.user_id
                LEFT JOIN minecraft_profiles mp_to ON i.to_user_id = mp_to.user_id
                WHERE i.to_user_id = ? AND i.status = 'PENDING'
                ORDER BY i.created_at DESC
            """.trimIndent()),
    parameterSetter = { statement, userId ->
        statement.setInt(1, userId)
    },
    resultMapper = { resultSet ->
        buildList {
            while (resultSet.next()) {
                add(resultSet.toInvite())
            }
        }
    }
)


