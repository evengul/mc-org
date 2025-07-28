package app.mcorg.presentation.mockdata

import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.model.user.Profile
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.user.WorldMember
import java.time.ZonedDateTime

object MockUsers {
    object Evegul {
        const val ID = 1

        fun worldMember(displayName: String = "evegul", worldRole: Role = Role.ADMIN): WorldMember {
            return WorldMember(
                id = ID,
                displayName = displayName,
                worldRole = worldRole,
                createdAt = ZonedDateTime.now(),
                updatedAt = ZonedDateTime.now(),
            )
        }

        fun profile(email: String = "evegul@mcorg.app"): Profile {
            return Profile(
                id = ID,
                email = email
            )
        }

        fun tokenProfile(displayName: String = "evegul"): TokenProfile {
            val (uuid, username) = minecraftProfile()
            return TokenProfile(
                id = ID,
                uuid = uuid,
                minecraftUsername = username,
                displayName = displayName,
            )
        }

        fun minecraftProfile(): MinecraftProfile {
            return MinecraftProfile(
                uuid = "evegul-uuid",
                username = "evegul"
            )
        }
    }

    object Alex {
        const val ID = 2

        fun worldMember(displayName: String = "Alex", worldRole: Role = Role.MEMBER): WorldMember {
            return WorldMember(
                id = ID,
                displayName = displayName,
                worldRole = worldRole,
                createdAt = ZonedDateTime.now(),
                updatedAt = ZonedDateTime.now(),
            )
        }

        fun profile(email: String = "alex@mcorg.app"): Profile {
            return Profile(
                id = ID,
                email = email
            )
        }

        fun tokenProfile(displayName: String = "Alex"): TokenProfile {
            val (uuid, username) = minecraftProfile()
            return TokenProfile(
                id = ID,
                uuid = uuid,
                minecraftUsername = username,
                displayName = displayName,
            )
        }

        fun minecraftProfile(): MinecraftProfile {
            return MinecraftProfile(
                uuid = "alex-uuid",
                username = "Alex"
            )
        }
    }

    fun getWorldMembers() = listOf(
        Evegul.worldMember(),
        Alex.worldMember()
    )
}