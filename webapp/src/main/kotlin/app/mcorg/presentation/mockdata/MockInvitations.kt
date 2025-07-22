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
        role = Role.ADMIN,
        createdAt = mockZonedDateTime(2025, 7, 20),
        expiresAt = ZonedDateTime.now().plusDays(7),
        status = InviteStatus.Pending(
            createdAt = mockZonedDateTime(2025, 7, 20)
        )
    )

    private val invitations = mutableListOf(
        fantasyRealm
    )

    fun getPending() = invitations.filter { it.status is InviteStatus.Pending }

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