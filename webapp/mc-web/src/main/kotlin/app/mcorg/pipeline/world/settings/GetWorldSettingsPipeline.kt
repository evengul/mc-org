package app.mcorg.pipeline.world.settings

import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.pipelineResult
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraftfiles.GetSupportedVersionsStep
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.pipeline.world.commonsteps.GetWorldStep
import app.mcorg.pipeline.world.extractors.toWorldMembers
import app.mcorg.pipeline.world.invitations.CountWorldInvitationsStep
import app.mcorg.pipeline.world.invitations.GetWorldInvitationsStep
import app.mcorg.pipeline.world.invitations.InvitationStatusFilter
import app.mcorg.pipeline.world.settings.invitations.handleGetInvitationListFragment
import app.mcorg.presentation.templated.settings.SettingsPageData
import app.mcorg.presentation.templated.settings.worldSettingsPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleGetWorldSettings() {
    val user = this.getUser()
    val worldId = this.getWorldId()

    val isHtmxRequest = request.headers["HX-Request"] == "true"
    val rawStatus = request.queryParameters["status"]
    val status = InvitationStatusFilter.fromApiName(rawStatus, default = null)

    // HTMX request asking for the invitation list fragment only
    if (isHtmxRequest && status != null) {
        handleGetInvitationListFragment()
        return
    }

    val statusFilter = status ?: InvitationStatusFilter.PENDING

    val dataResult = handleGetSettingsPageData(worldId, statusFilter)
    if (dataResult is Result.Failure) {
        respond(HttpStatusCode.InternalServerError)
        return
    }

    val data = (dataResult as Result.Success).value

    respondHtml(worldSettingsPage(user, data))
}

suspend fun ApplicationCall.handleGetSettingsPageData(
    worldId: Int,
    statusFilter: InvitationStatusFilter,
): Result<AppFailure.DatabaseError, SettingsPageData> {
    val user = this.getUser()
    val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()
    val isOwner = ValidateWorldMemberRole<Unit>(user, Role.OWNER, worldId).process(Unit) is Result.Success
    val currentUserRole = if (isOwner) Role.OWNER else Role.ADMIN

    return pipelineResult {
        val (world, invitations, counts, members) = parallel(
            { GetWorldStep.run(worldId) },
            { GetWorldInvitationsStep(worldId).run(statusFilter) },
            { CountWorldInvitationsStep(worldId).run(Unit) },
            { getWorldMembersStep.run(worldId) },
        )
        SettingsPageData(
            world = world,
            supportedVersions = supportedVersions,
            currentUser = user,
            currentUserRole = currentUserRole,
            members = members,
            invitations = invitations,
            invitationCounts = counts,
            statusFilter = statusFilter,
        )
    }
}

private val getWorldMembersStep = DatabaseSteps.query<Int, List<WorldMember>>(
    SafeSQL.select("""
        SELECT
            id,
            user_id,
            world_id,
            display_name,
            world_role,
            updated_at,
            created_at
        FROM
            world_members
            WHERE world_id = ?;
    """.trimIndent()),
    parameterSetter = { statement, input ->
        statement.setInt(1, input)
    },
    resultMapper = { it.toWorldMembers() }
)
