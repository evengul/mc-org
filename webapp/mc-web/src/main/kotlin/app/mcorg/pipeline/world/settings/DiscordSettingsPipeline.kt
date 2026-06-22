package app.mcorg.pipeline.world.settings

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.settings.discordConnections
import app.mcorg.presentation.templated.settings.renderDiscordSectionBody
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.webhook.CreateWebhookSubscriptionInput
import app.mcorg.webhook.CreateWebhookSubscriptionStep
import app.mcorg.webhook.DeleteWorldWebhookSubscriptionInput
import app.mcorg.webhook.DeleteWorldWebhookSubscriptionStep
import app.mcorg.webhook.WebhookCallbackUrl
import app.mcorg.webhook.WebhookStore
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond

/** Discord snowflake — 15–21 decimal digits. */
private val CHANNEL_ID_PATTERN = Regex("""^\d{15,21}$""")

private fun validationError(failure: ValidationFailure) = AppFailure.ValidationError(listOf(failure))

/** Whether the Discord integration is wired up for this instance (Worker URL + shared secret set). */
fun discordConfigured(): Boolean =
    !AppConfig.seamDiscordUrl.isNullOrBlank() && !AppConfig.webhookSharedSecret.isNullOrBlank()

/**
 * Build the seam-discord callback URL for a channel:
 * `<SEAM_DISCORD_URL>/seam-events/<channelId>` (+ `?compact=1` when compact). Pure helper.
 */
fun buildDiscordCallbackUrl(seamDiscordUrl: String, channelId: String, compact: Boolean): String {
    val base = "${seamDiscordUrl.trimEnd('/')}/seam-events/$channelId"
    return if (compact) "$base?compact=1" else base
}

private suspend fun ApplicationCall.respondDiscordSection(worldId: Int) {
    val subscriptions = WebhookStore.findActiveSubscriptions(worldId)
    val connections = discordConnections(subscriptions, AppConfig.seamDiscordUrl)
    respondHtml(renderDiscordSectionBody(worldId, configured = true, connections = connections))
}

suspend fun ApplicationCall.handleConnectDiscord() {
    val worldId = this.getWorldId()
    val parameters = this.receiveParameters()

    // Fail closed when the integration isn't configured — never create a subscription pointing nowhere.
    if (!discordConfigured()) {
        respondHtml(
            renderDiscordSectionBody(worldId, configured = false, connections = emptyList()),
            HttpStatusCode.ServiceUnavailable,
        )
        return
    }
    val seamDiscordUrl = AppConfig.seamDiscordUrl!!
    val sharedSecret = AppConfig.webhookSharedSecret!!

    handlePipeline(
        onSuccess = { respondDiscordSection(worldId) },
    ) {
        val channelId = ValidationSteps.required("channel_id", ::validationError).run(parameters)
        ValidationSteps.validateCustom<AppFailure.ValidationError, String>(
            "channel_id",
            "Must be a Discord channel ID (15–21 digits)",
            ::validationError,
        ) { CHANNEL_ID_PATTERN.matches(it) }.run(channelId)

        val compact = parameters.isChecked("compact")
        val callbackUrl = buildDiscordCallbackUrl(seamDiscordUrl, channelId, compact)

        // SSRF guard on the resolved callback (the Worker base is operator config, but validate anyway).
        ValidationSteps.validateCustom<AppFailure.ValidationError, String>(
            "channel_id",
            "The configured Discord URL is not a safe public callback target",
            ::validationError,
        ) { WebhookCallbackUrl.isSafe(callbackUrl, requireHttps = AppConfig.env == Production) }.run(channelId)

        val metadataJson = """{"discord_channel_id":"$channelId","compact":$compact}"""
        CreateWebhookSubscriptionStep.run(
            CreateWebhookSubscriptionInput(
                worldId = worldId,
                callbackUrl = callbackUrl,
                secret = sharedSecret,
                eventFilterJson = """["*"]""",
                metadataJson = metadataJson,
            )
        )
    }
}

suspend fun ApplicationCall.handleDisconnectDiscord() {
    val worldId = this.getWorldId()
    val subscriptionId = parameters["subscriptionId"]?.toIntOrNull()
    if (subscriptionId == null) {
        respond(HttpStatusCode.BadRequest, "Invalid subscription id")
        return
    }
    handlePipeline(
        onSuccess = { respondDiscordSection(worldId) },
    ) {
        DeleteWorldWebhookSubscriptionStep.run(
            DeleteWorldWebhookSubscriptionInput(subscriptionId = subscriptionId, worldId = worldId)
        )
    }
}

/** Treat a checkbox parameter as checked when present and not an explicit "false"/"off"/"0". */
private fun Parameters.isChecked(name: String): Boolean {
    val raw = this[name]?.lowercase()?.trim() ?: return false
    return raw !in setOf("false", "off", "0", "")
}
