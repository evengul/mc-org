package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.invite.InviteStatus
import app.mcorg.domain.model.user.Role
import java.time.ZonedDateTime

object MockInvitations {
    private val fantasyRealm = Invite(
        id = 1,
        worldId = MockWorlds.fantasyRealm.id,
        worldName = MockWorlds.fantasyRealm.name,
        from = MockUsers.Alex.ID,
        fromUsername = MockUsers.Alex.minecraftProfile().username,
        to = MockUsers.Evegul.ID,
        toUsername = MockUsers.Evegul.minecraftProfile().username,
        role = Role.ADMIN,
        createdAt = mockZonedDateTime(2025, 7, 20),
        expiresAt = ZonedDateTime.now().plusDays(7),
        status = InviteStatus.Pending(
            createdAt = mockZonedDateTime(2025, 7, 20)
        )
    )

    private val survivalInvitesAlex = Invite(
        id = 2,
        worldId = MockWorlds.survivalWorld.id,
        worldName = MockWorlds.survivalWorld.name,
        from = MockUsers.Evegul.ID,
        fromUsername = MockUsers.Evegul.minecraftProfile().username,
        to = MockUsers.Alex.ID,
        toUsername = MockUsers.Alex.minecraftProfile().username,
        role = Role.ADMIN,
        createdAt = mockZonedDateTime(2025, 7, 21),
        expiresAt = ZonedDateTime.now().plusDays(7),
        status = InviteStatus.Accepted(
            acceptedAt = mockZonedDateTime(2025, 7, 21)
        )
    )

    private val survivalInvitesNotch = Invite(
        id = 3,
        worldId = MockWorlds.survivalWorld.id,
        worldName = MockWorlds.survivalWorld.name,
        from = MockUsers.Evegul.ID,
        fromUsername = MockUsers.Evegul.minecraftProfile().username,
        to = -1,
        toUsername = "Notch",
        role = Role.MEMBER,
        createdAt = mockZonedDateTime(2025, 7, 22),
        expiresAt = ZonedDateTime.now().plusDays(7),
        status = InviteStatus.Pending(
            createdAt = mockZonedDateTime(2025, 7, 22)
        )
    )

    private val invitations = mutableListOf(
        fantasyRealm,
        survivalInvitesAlex,
        survivalInvitesNotch,
    )

    fun getPendingByToId(userId: Int) = invitations.filter { it.status is InviteStatus.Pending && it.to == userId }

    fun getByWorldId(worldId: Int) = invitations.filter { it.worldId == worldId }

    fun accept(id: Int) {
        val index = invitations.indexOfFirst { it.id == id }
        invitations[index] = invitations[index].copy(
            status = (invitations[index].status as InviteStatus.Pending).accept()
        )

        MockWorlds.getList().find { it.id == invitations[index].worldId }?.let {
            MockWorlds.add(it)
        }
    }

    fun decline(id: Int) {
        val index = invitations.indexOfFirst { it.id == id }
        invitations[index] = invitations[index].copy(
            status = (invitations[index].status as InviteStatus.Pending).decline()
        )
    }
}