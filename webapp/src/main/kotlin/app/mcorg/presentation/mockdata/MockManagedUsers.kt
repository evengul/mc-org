package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.admin.ManagedUser
import app.mcorg.domain.model.user.Role

object MockManagedUsers {
    fun getMockedManagedUsers() = listOf(
        ManagedUser(
            id = MockUsers.Evegul.ID,
            displayName = "evegul",
            minecraftUsername = "evegul",
            email = "even@mcorg.app",
            globalRole = Role.ADMIN,
            joinedAt = mockZonedDateTime(2021, 1, 1)
        ),
        ManagedUser(
            id = -1,
            displayName = "Steve",
            minecraftUsername = "steve",
            email = "steve@minecraft.net",
            globalRole = Role.ADMIN,
            joinedAt = mockZonedDateTime(2023, 1, 1)
        ),
        ManagedUser(
            id = -2,
            displayName = "Alex",
            minecraftUsername = "alex",
            email = "alex@minecraft.net",
            globalRole = Role.MEMBER,
            joinedAt = mockZonedDateTime(2023, 2, 1)
        ),
        ManagedUser(
            -3,
            displayName = "Notch",
            minecraftUsername = "notch",
            email = "notch@minecraft.net",
            globalRole = Role.BANNED,
            joinedAt = mockZonedDateTime(2023, 3, 1)
        )
    )
}