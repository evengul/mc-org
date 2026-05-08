package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.world.invitations.CountWorldInvitationsResult
import app.mcorg.pipeline.world.invitations.InvitationStatusFilter
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.p

data class SettingsPageData(
    val world: World,
    val supportedVersions: List<MinecraftVersion.Release>,
    val currentUser: TokenProfile,
    val currentUserRole: Role,
    val members: List<WorldMember>,
    val invitations: List<Invite>,
    val invitationCounts: CountWorldInvitationsResult,
    val statusFilter: InvitationStatusFilter,
)

fun worldSettingsPage(user: TokenProfile, data: SettingsPageData): String = pageShell(
    pageTitle = "MC-ORG — ${data.world.name} Settings",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/form.css",
        "/static/styles/components/danger-zone.css",
        "/static/styles/components/avatar.css",
        "/static/styles/pages/settings-page.css",
    )
) {
    appHeader(
        worldName = data.world.name,
        worldId = data.world.id,
        user = user,
        isWorldAdmin = true,
        breadcrumbBlock = {
            link("Worlds", "/worlds")
                .link(data.world.name, "/worlds/${data.world.id}/projects")
                .current("Settings")
        }
    )
    main {
        container {
            div("settings-page__heading") {
                h1("settings-page__title") { +"World Settings" }
                p("settings-page__subtitle") { +"Manage your world settings, members, and invitations" }
            }
            div("settings-page__sections") {
                id = "world-settings-content"
                generalSection(data)
                invitationsSection(data)
                membersSection(data.currentUser, data.members)
                if (data.currentUserRole == Role.OWNER) {
                    dangerSection(data.world)
                }
            }
        }
    }
}
