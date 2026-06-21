package app.mcorg.webhook

import app.mcorg.config.AppConfig
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
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
import java.security.MessageDigest

/** Header carrying the shared secret for the webhook admin endpoints. */
const val WEBHOOK_ADMIN_SECRET_HEADER = "X-Seam-Admin-Secret"

private val adminJson = Json

/**
 * Route-scoped gate for the machine-facing webhook admin endpoints. Fails closed: if no
 * `WEBHOOK_ADMIN_SECRET` is configured the endpoints are inert (503). Otherwise the request's
 * [WEBHOOK_ADMIN_SECRET_HEADER] must match it (constant-time compare) or the call is rejected 401.
 */
val WebhookAdminAuthPlugin = createRouteScopedPlugin("WebhookAdminAuthPlugin") {
    onCall { call ->
        val configured = AppConfig.webhookAdminSecret
        if (configured.isNullOrBlank()) {
            call.respond(HttpStatusCode.ServiceUnavailable, "Webhook admin endpoint is not configured")
            return@onCall
        }
        val provided = call.request.headers[WEBHOOK_ADMIN_SECRET_HEADER]
        if (provided == null || !constantTimeEquals(provided, configured)) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid or missing admin secret")
        }
    }
}

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
        ValidationSteps.validateUrl("callback_url", ::validationError).run(callbackUrl)
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

private fun Parameters.orDefault(name: String, default: String): String =
    this[name]?.takeIf { it.isNotBlank() } ?: default

private fun parsesAsStringList(raw: String): Boolean =
    runCatching { adminJson.decodeFromString(ListSerializer(String.serializer()), raw) }.isSuccess

private fun parsesAsJsonObject(raw: String): Boolean =
    runCatching { adminJson.decodeFromString(JsonObject.serializer(), raw) }.isSuccess

private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
