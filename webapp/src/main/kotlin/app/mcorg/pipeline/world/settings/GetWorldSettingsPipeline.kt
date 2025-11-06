package app.mcorg.pipeline.world.settings

import app.mcorg.domain.model.user.WorldMember
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.GetSupportedVersionsStep
import app.mcorg.pipeline.world.commonsteps.GetWorldStep
import app.mcorg.pipeline.world.extractors.toWorldMembers
import app.mcorg.pipeline.world.invitations.CountWorldInvitationsStep
import app.mcorg.pipeline.world.invitations.GetWorldInvitationsStep
import app.mcorg.pipeline.world.invitations.InvitationStatusFilter
import app.mcorg.pipeline.world.settings.invitations.handleGetInvitationListFragment
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.settings.*
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetWorldSettings() {
    val user = this.getUser()
    val worldId = this.getWorldId()

    val isHtmxRequest = request.headers["HX-Request"] == "true"

    val tab = request.queryParameters["tab"]?.let {
        when(it) {
            "general", "members", "statistics" -> it
            else -> null // Invalid tab, return null
        }
    }

    val status = request.queryParameters["status"].let { InvitationStatusFilter.fromApiName(it, default = null) }

    if (tab == null && isHtmxRequest && status != null) {
        handleGetInvitationListFragment()
        return
    }

    val tabData = when(tab) {
        "members" -> handleGetMembersTabData(worldId, status ?: InvitationStatusFilter.PENDING)
        "statistics" -> handleGetStatisticsTabData(worldId)
        else -> handleGetGeneralTabData(worldId) // General tab or no tab specified
    }

    if (tabData is Result.Failure) {
        respond(HttpStatusCode.InternalServerError)
        return
    }

    val tabDataResult = (tabData as Result.Success).value

    if (request.headers["HX-Request"] == "true") {
        // For HTMX requests, handle specific tabs
        respondHtml(
            createHTML().div {
                classes += "world-settings-content"
                when (tabDataResult) {
                    is SettingsTab.General -> generalTab(tabDataResult)
                    is SettingsTab.Members -> membersTab(tabDataResult)
                    is SettingsTab.Statistics -> statisticsTab(tabDataResult)
                }
            }
        )
    } else {
        respondHtml(worldSettingsPage(user, tabDataResult))
    }
}

suspend fun ApplicationCall.handleGetMembersTabData(worldId: Int, statusFilter: InvitationStatusFilter): Result<AppFailure.DatabaseError, SettingsTab.Members> {
    val user = this.getUser()
    val worldPipeline = Pipeline.create<AppFailure.DatabaseError, Int>()
        .pipe(Step.value(worldId))
        .pipe(GetWorldStep)

    val invitationsPipeline = Pipeline.create<AppFailure.DatabaseError, Unit>()
        .map { statusFilter }
        .pipe(GetWorldInvitationsStep(worldId))

    val invitationCountPipeline = Pipeline.create<AppFailure.DatabaseError, Unit>()
        .pipe(CountWorldInvitationsStep(worldId))

    val membersPipeline = Pipeline.create<AppFailure.DatabaseError, Int>()
        .pipe(Step.value(worldId))
        .pipe(getWorldMembersStep)

    return executeParallelPipeline {
        val world = pipeline("world", worldId, worldPipeline)
        val invitations = pipeline("invitations", Unit, invitationsPipeline)
        val counts = pipeline("invitationCounts", Unit, invitationCountPipeline)
        val members = pipeline("members", worldId, membersPipeline)

        val invitationsWithCount = merge("invitationsWithCount", invitations, counts) { i, c ->
            Result.success(i to c)
        }

        merge("data", world, invitationsWithCount, members) { w, ic, m ->
            Result.success(SettingsTab.Members(
                currentUser = user,
                world = w,
                invitations = ic.first,
                invitationCounts = ic.second,
                members = m
            ))
        }
    }
}

suspend fun ApplicationCall.handleGetGeneralTabData(worldId: Int): Result<AppFailure.DatabaseError, SettingsTab.General> {
    val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()
    return executeParallelPipeline {
        pipeline(
            id = "world-general-tab",
            input = parameters,
            pipeline = Pipeline.create<AppFailure.DatabaseError, Parameters>()
                .pipe(Step.value(worldId))
                .pipe(GetWorldStep)
                .map { SettingsTab.General(it, supportedVersions) }
        )
    }
}

suspend fun ApplicationCall.handleGetStatisticsTabData(worldId: Int): Result<AppFailure.DatabaseError, SettingsTab.Statistics> {
    return executeParallelPipeline {
        pipeline(
            id = "world-statistics-tab",
            input = parameters,
            pipeline = Pipeline.create<AppFailure.DatabaseError, Parameters>()
                .pipe(Step.value(worldId))
                .pipe(GetWorldStep)
                .map { SettingsTab.Statistics(it) }
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
