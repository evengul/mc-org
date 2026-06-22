package app.mcorg.presentation.templated.settings

import app.mcorg.presentation.*
import app.mcorg.presentation.templated.dsl.BadgeVariant
import app.mcorg.presentation.templated.dsl.Link
import app.mcorg.presentation.templated.dsl.badge
import app.mcorg.presentation.templated.dsl.section
import app.mcorg.webhook.WebhookSubscription
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * A Discord channel connected to a world, derived from a [WebhookSubscription] whose callback URL
 * points at the seam-discord Worker. The channel id and compact flag live in the callback URL
 * (`<base>/seam-events/<channelId>?compact=1`), so they can be reconstructed for display without
 * reading the subscription metadata column.
 */
data class DiscordConnection(
    val subscriptionId: Int,
    val channelId: String,
    val compact: Boolean,
)

private val SEAM_EVENTS_PATH = Regex("""/seam-events/(\d{15,21})(?:/)?$""")

/**
 * Parse a subscription callback URL into a [DiscordConnection], or null if it is not a Discord
 * seam-events URL. Pure helper, unit-tested.
 */
fun parseDiscordConnection(subscriptionId: Int, callbackUrl: String): DiscordConnection? {
    val (path, query) = callbackUrl.substringBefore('#').let {
        it.substringBefore('?') to it.substringAfter('?', "")
    }
    val channelId = SEAM_EVENTS_PATH.find(path)?.groupValues?.get(1) ?: return null
    val compact = query.split('&').any { it == "compact=1" }
    return DiscordConnection(subscriptionId, channelId, compact)
}

/**
 * Reduce a world's active webhook subscriptions to the Discord ones connected through the configured
 * Worker base URL. Only subscriptions whose callback URL starts with [seamDiscordUrl] and matches
 * the seam-events shape are surfaced here.
 */
fun discordConnections(subscriptions: List<WebhookSubscription>, seamDiscordUrl: String?): List<DiscordConnection> {
    if (seamDiscordUrl.isNullOrBlank()) return emptyList()
    val base = seamDiscordUrl.trimEnd('/')
    return subscriptions
        .filter { it.callbackUrl.startsWith(base) }
        .mapNotNull { parseDiscordConnection(it.id, it.callbackUrl) }
}

fun DIV.discordSection(data: SettingsPageData) {
    section(
        title = "Discord",
        subtitle = "Connect this world's notifications to a Discord channel.",
    ) {
        div("section__card") {
            id = "discord-section"
            discordSectionBody(data.world.id, data.discordConfigured, data.discordConnections)
        }
    }
}

/**
 * The inner body of the Discord section — the connect form (or not-configured notice) plus the list
 * of connected channels. Emits the single `.discord-section` child of `#discord-section`. Rendered
 * both on full page load and as the HTMX `innerHTML` swap of `#discord-section` after a
 * connect/disconnect.
 */
fun FlowContent.discordSectionBody(
    worldId: Int,
    configured: Boolean,
    connections: List<DiscordConnection>,
) {
    div("discord-section") {
        if (!configured) {
            p("subtle") { +"Discord integration isn't configured for this Seam instance yet." }
            return@div
        }
        connectDiscordForm(worldId)
        discordConnectionsList(worldId, connections)
    }
}

private fun FlowContent.connectDiscordForm(worldId: Int) {
    div("connect-discord") {
        h3 { +"Connect a channel" }
        form("connect-discord__form") {
            encType = FormEncType.applicationXWwwFormUrlEncoded
            hxTarget("#discord-section")
            attributes["hx-target-error"] = ".validation-error-message"
            hxSwap("innerHTML")
            hxPost("${Link.Worlds.world(worldId).settings().to}/discord")
            attributes["hx-on::after-request"] = """
                if (event.detail.xhr.status >= 200 && event.detail.xhr.status < 300) {
                        this.reset();
                }
            """.trimIndent()
            div("connect-discord__inputs") {
                div("input-group") {
                    label {
                        htmlFor = "discord-channel-id-input"
                        +"Discord Channel ID"
                        span("required-indicator") { +"*" }
                    }
                    input(type = InputType.text, name = "channel_id", classes = "form-control") {
                        id = "discord-channel-id-input"
                        placeholder = "123456789012345678"
                        required = true
                        attributes["inputmode"] = "numeric"
                        attributes["pattern"] = """\d{15,21}"""
                    }
                    p("settings-form__helper subtle") {
                        +"Right-click a channel in Discord (with Developer Mode on) and choose “Copy Channel ID”."
                    }
                }
                div("input-group input-group--checkbox") {
                    label("checkbox-label") {
                        htmlFor = "discord-compact-input"
                        input(type = InputType.checkBox, name = "compact", classes = "form-check-input") {
                            id = "discord-compact-input"
                            value = "true"
                        }
                        +"Compact messages"
                    }
                }
            }
            p("validation-error-message") { id = "validation-error-channel_id" }
            div("connect-discord__actions") {
                button {
                    classes = setOf("btn", "btn--primary")
                    type = ButtonType.submit
                    +"Connect"
                }
            }
        }
    }
}

private fun FlowContent.discordConnectionsList(worldId: Int, connections: List<DiscordConnection>) {
    div("discord-connections") {
        h3 { +"Connected channels" }
        ul("person-row-list") {
            id = "discord-connections-list"
            if (connections.isEmpty()) {
                li("person-row") {
                    id = "empty-discord-connections-list"
                    div("person-row__start") {
                        p("subtle") { +"No Discord channels connected yet." }
                    }
                }
                return@ul
            }
            connections.forEach { discordConnectionRow(worldId, it) }
        }
    }
}

private fun UL.discordConnectionRow(worldId: Int, connection: DiscordConnection) {
    li("person-row") {
        id = "discord-connection-${connection.subscriptionId}"
        div("person-row__start") {
            div("person-row__info") {
                p("person-row__name") { +"Channel ${connection.channelId}" }
                if (connection.compact) {
                    div("row") { badge("Compact", BadgeVariant.NEUTRAL) }
                }
            }
        }
        div("person-row__end") {
            button {
                classes = setOf("btn", "btn--ghost", "btn--sm")
                type = ButtonType.button
                hxDeleteWithConfirm(
                    url = "${Link.Worlds.world(worldId).settings().to}/discord/${connection.subscriptionId}",
                    title = "Disconnect Channel",
                    description = "Are you sure you want to disconnect this Discord channel? It will stop receiving notifications.",
                )
                hxTarget("#discord-section")
                hxSwap("innerHTML")
                +"Disconnect"
            }
        }
    }
}

/** Top-level fragment renderer for the Discord section body, used as the HTMX `innerHTML` response
 *  after a connect/disconnect so HTMX swaps `#discord-section`'s contents with a fresh
 *  `.discord-section`. */
fun renderDiscordSectionBody(
    worldId: Int,
    configured: Boolean,
    connections: List<DiscordConnection>,
): String = createHTML().div("discord-section") {
    if (!configured) {
        p("subtle") { +"Discord integration isn't configured for this Seam instance yet." }
        return@div
    }
    connectDiscordForm(worldId)
    discordConnectionsList(worldId, connections)
}
