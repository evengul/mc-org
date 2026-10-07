package app.mcorg.webhook

import app.mcorg.logging.redacted

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.plugins.MachineEndpointAuthPlugin
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
import java.sql.PreparedStatement

private val adminJson = Json

/**
 * Registers the v1 webhook subscription management surface. Mounted at `/integrations/webhooks`
 * (outside the user app's JWT auth — see AuthPlugin's allowlist) and gated solely by the shared
 * secret. This is the simplified setup path until per-user `/seam connect` (Phase 3) and the JSON
 * API (MCO-235) land. Responses are tiny HTML fragments, consistent with the rest of the app.
 */
fun Route.webhookAdminRoutes() {
    route("/integrations/webhooks") {
        install(MachineEndpointAuthPlugin)
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

        CreateAdminWebhookSubscriptionStep.run(
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
) {
    // MCO-340: `secret` arrives as a raw request parameter and becomes the subscription's
    // signing key.
    override fun toString() = "CreateWebhookSubscriptionInput(worldId=$worldId, " +
        "callbackUrl=$callbackUrl, secret=${redacted(secret)}, " +
        "eventFilterJson=$eventFilterJson, metadataJson=$metadataJson)"
}

/**
 * Inserts a subscription as given. Used by the operator endpoint and by tests.
 *
 * A second active subscription for a Discord channel the world already has fails on V2_74_0's
 * unique index with [AppFailure.DatabaseError.IntegrityConstraintError]. That is deliberate: this
 * path replaces nothing, so it cannot quietly repoint a user's connection. Connecting from world
 * settings goes through [UpsertDiscordSubscriptionStep] instead.
 */
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
            parameterSetter = ::bindSubscription,
        ).process(input)
}

/**
 * Connects a Discord channel from world settings: creates the subscription, or, when the world
 * already has an active one for the same channel, replaces its URL, secret, filter and metadata in
 * place and returns its id (MCO-424). Reconnecting a channel is how a user picks up a new event
 * filter (`documentation/webhook-contract.md`), and a plain INSERT made that post every event twice.
 *
 * The conflict target is V2_74_0's partial unique index on the metadata's `discord_channel_id`,
 * so the input's metadata must carry it.
 */
object UpsertDiscordSubscriptionStep : Step<CreateWebhookSubscriptionInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: CreateWebhookSubscriptionInput) =
        DatabaseSteps.update<CreateWebhookSubscriptionInput>(
            sql = SafeSQL.insert(
                """
                INSERT INTO webhook_subscriptions (world_id, callback_url, secret, event_filter, metadata)
                VALUES (?, ?, ?, ?::jsonb, ?::jsonb)
                ON CONFLICT (world_id, (metadata ->> 'discord_channel_id')) WHERE active = true
                DO UPDATE SET callback_url = EXCLUDED.callback_url,
                              secret = EXCLUDED.secret,
                              event_filter = EXCLUDED.event_filter,
                              metadata = EXCLUDED.metadata,
                              consecutive_failures = 0,
                              updated_at = CURRENT_TIMESTAMP
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = ::bindSubscription,
        ).process(input)
}

private fun bindSubscription(statement: PreparedStatement, input: CreateWebhookSubscriptionInput) {
    statement.setInt(1, input.worldId)
    statement.setString(2, input.callbackUrl)
    statement.setString(3, input.secret)
    statement.setString(4, input.eventFilterJson)
    statement.setString(5, input.metadataJson)
}

/** The operator endpoint's insert, with a duplicate channel reported as a validation error. */
private object CreateAdminWebhookSubscriptionStep : Step<CreateWebhookSubscriptionInput, AppFailure, Int> {
    override suspend fun process(input: CreateWebhookSubscriptionInput): Result<AppFailure, Int> {
        val result = CreateWebhookSubscriptionStep.process(input)
        if (result is Result.Failure && result.error is AppFailure.DatabaseError.IntegrityConstraintError) {
            // The same failure covers a foreign-key violation, so the message names both causes.
            return Result.failure(
                AppFailure.customValidationError(
                    "world_id",
                    "Unknown world, or the world already has an active subscription for this Discord channel",
                )
            )
        }
        return result
    }
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
