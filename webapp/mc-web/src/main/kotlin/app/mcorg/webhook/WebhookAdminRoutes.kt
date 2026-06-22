package app.mcorg.webhook

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.plugins.WebhookAdminAuthPlugin
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val adminJson = Json

/**
 * Registers the v1 webhook subscription management surface. Mounted at `/integrations/webhooks`
 * (outside the user app's JWT auth — see AuthPlugin's allowlist) and gated solely by the shared
 * secret. This is the simplified setup path until per-user `/seam connect` (Phase 3) and the JSON
 * API (MCO-235) land. Responses are tiny HTML fragments, consistent with the rest of the app.
 */
fun Route.webhookAdminRoutes() {
    route("/integrations/webhooks") {
        install(WebhookAdminAuthPlugin)
        post { call.handleCreateWebhookSubscription() }
        delete("/{subscriptionId}") { call.handleDeleteWebhookSubscription() }
    }
}

private fun validationError(it: app.mcorg.pipeline.failure.ValidationFailure) = AppFailure.ValidationError(listOf(it))

suspend fun ApplicationCall.handleCreateWebhookSubscription() {
    val parameters = receiveParameters()
    handlePipeline(
        onSuccess = { id ->
            respondHtml(createHTML().p { +"Created webhook subscription #$id" }, HttpStatusCode.Created)
        }
    ) {
        val worldId = ValidationSteps.requiredInt("world_id", ::validationError).run(parameters)
        val callbackUrl = ValidationSteps.required("callback_url", ::validationError).run(parameters)
        ValidationSteps.validateCustom<AppFailure.ValidationError, String>(
            "callback_url",
            "Must be a public http(s) URL (no loopback, private, or link-local hosts)",
            ::validationError,
        ) { WebhookCallbackUrl.isSafe(it, requireHttps = AppConfig.env == Production) }.run(callbackUrl)
        val secret = ValidationSteps.required("secret", ::validationError).run(parameters)
        ValidationSteps.validateLength("secret", minLength = 8, maxLength = 256, errorMapper = ::validationError).run(secret)

        val eventFilter = parameters.orDefault("event_filter", """["*"]""")
        ValidationSteps.validateCustom<AppFailure.ValidationError, String>(
            "event_filter", "Must be a JSON array of event-type strings", ::validationError
        ) { parsesAsStringList(it) }.run(eventFilter)

        val metadata = parameters.orDefault("metadata", "{}")
        ValidationSteps.validateCustom<AppFailure.ValidationError, String>(
            "metadata", "Must be a JSON object", ::validationError
        ) { parsesAsJsonObject(it) }.run(metadata)

        CreateWebhookSubscriptionStep.run(
            CreateWebhookSubscriptionInput(worldId, callbackUrl, secret, eventFilter, metadata)
        )
    }
}

suspend fun ApplicationCall.handleDeleteWebhookSubscription() {
    val id = parameters["subscriptionId"]?.toIntOrNull()
    if (id == null) {
        respond(HttpStatusCode.BadRequest, "Invalid subscription id")
        return
    }
    handlePipeline(
        onSuccess = { affected ->
            if (affected > 0) {
                respondHtml(createHTML().p { +"Deleted webhook subscription #$id" })
            } else {
                respondHtml(createHTML().p { +"No webhook subscription #$id" }, HttpStatusCode.NotFound)
            }
        }
    ) {
        DeleteWebhookSubscriptionStep.run(id)
    }
}

data class CreateWebhookSubscriptionInput(
    val worldId: Int,
    val callbackUrl: String,
    val secret: String,
    val eventFilterJson: String,
    val metadataJson: String,
)

object CreateWebhookSubscriptionStep : Step<CreateWebhookSubscriptionInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: CreateWebhookSubscriptionInput) =
        DatabaseSteps.update<CreateWebhookSubscriptionInput>(
            sql = SafeSQL.insert(
                """
                INSERT INTO webhook_subscriptions (world_id, callback_url, secret, event_filter, metadata)
                VALUES (?, ?, ?, ?::jsonb, ?::jsonb)
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { statement, i ->
                statement.setInt(1, i.worldId)
                statement.setString(2, i.callbackUrl)
                statement.setString(3, i.secret)
                statement.setString(4, i.eventFilterJson)
                statement.setString(5, i.metadataJson)
            },
        ).process(input)
}

object DeleteWebhookSubscriptionStep : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int) =
        DatabaseSteps.update<Int>(
            sql = SafeSQL.delete("DELETE FROM webhook_subscriptions WHERE id = ?"),
            parameterSetter = { statement, id -> statement.setInt(1, id) },
        ).process(input)
}

data class DeleteWorldWebhookSubscriptionInput(val subscriptionId: Int, val worldId: Int)

/**
 * World-scoped delete used by the world-settings Discord surface (MCO-240). Unlike the id-only
 * [DeleteWebhookSubscriptionStep] (operator/shared-secret path), this also matches `world_id` so a
 * world admin can never delete another world's subscription by guessing its id.
 */
object DeleteWorldWebhookSubscriptionStep : Step<DeleteWorldWebhookSubscriptionInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: DeleteWorldWebhookSubscriptionInput) =
        DatabaseSteps.update<DeleteWorldWebhookSubscriptionInput>(
            sql = SafeSQL.delete("DELETE FROM webhook_subscriptions WHERE id = ? AND world_id = ?"),
            parameterSetter = { statement, i ->
                statement.setInt(1, i.subscriptionId)
                statement.setInt(2, i.worldId)
            },
        ).process(input)
}

private fun Parameters.orDefault(name: String, default: String): String =
    this[name]?.takeIf { it.isNotBlank() } ?: default

private fun parsesAsStringList(raw: String): Boolean =
    runCatching { adminJson.decodeFromString(ListSerializer(String.serializer()), raw) }.isSuccess

private fun parsesAsJsonObject(raw: String): Boolean =
    runCatching { adminJson.decodeFromString(JsonObject.serializer(), raw) }.isSuccess
