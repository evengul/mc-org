package app.mcorg.pipeline.profile

import app.mcorg.domain.model.user.Profile
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.failure.NotificationFailures
import app.mcorg.pipeline.notification.GetUnreadNotificationCountStep
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.profile.profilePage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

private object GetProfileStep : Step<Int, DatabaseFailure, Profile> {
    override suspend fun process(input: Int): Result<DatabaseFailure, Profile> {
        val step = DatabaseSteps.query<Int, DatabaseFailure, Profile?>(
            sql = SafeSQL.select("SELECT id, email FROM users WHERE id = ? LIMIT 1"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, input) },
            errorMapper = { error -> error },
            { rs ->
                if (rs.next()) {
                    Profile(
                        id = rs.getInt("id"),
                        email = rs.getString("email")
                    )
                } else {
                    null
                }
            }
        )
        return when (val result = step.process(input)) {
            is Result.Success -> {
                val profile = result.value
                if (profile != null) {
                    Result.Success(profile)
                } else {
                    Result.Failure(DatabaseFailure.NotFound)
                }
            }

            is Result.Failure -> Result.Failure(result.error)
        }
    }
}

interface HandleGetProfileFailure {
    data class DatabaseError(val reason: DatabaseFailure) : HandleGetProfileFailure
    data class NotificationError(val reason: NotificationFailures) : HandleGetProfileFailure
}

suspend fun ApplicationCall.handleGetProfile() {
    val user = getUser()

    val profilePipeline = Pipeline.create<DatabaseFailure, Int>()
        .pipe(GetProfileStep)
        .mapFailure { HandleGetProfileFailure.DatabaseError(it) }

    val notificationsPipeline = Pipeline.create<NotificationFailures, Int>()
        .pipe(GetUnreadNotificationCountStep)
        .mapFailure { HandleGetProfileFailure.NotificationError(it) }
        .recover { Result.success(0) }

    executeParallelPipeline(
        onSuccess = { (profile, notificationCount) -> respondHtml(profilePage(user, profile, notificationCount)) },
        onFailure = { respondHtml("An error occurred") }
    ) {
        val profile = pipeline("profile", user.id, profilePipeline)
        val notifications = pipeline("notifications", user.id, notificationsPipeline)
        merge("data", profile, notifications) { p, n -> Result.success(Pair(p, n)) }
    }
}