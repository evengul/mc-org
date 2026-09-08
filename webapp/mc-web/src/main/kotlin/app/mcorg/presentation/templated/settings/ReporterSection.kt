package app.mcorg.presentation.templated.settings

import app.mcorg.api.MintedReporterToken
import app.mcorg.api.ReporterTokenRow
import app.mcorg.presentation.*
import app.mcorg.presentation.templated.dsl.BadgeVariant
import app.mcorg.presentation.templated.dsl.Link
import app.mcorg.presentation.templated.dsl.badge
import app.mcorg.presentation.templated.dsl.section
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * World settings → connected server (MCO-531).
 *
 * A world with tagged containers and no reporter must say so **loudly**. It is the diagnosable
 * failure when a client and a server disagree about which Seam world they are in: tags accumulate,
 * nothing ever sweeps them, and every count stays at zero. Without this panel someone is left
 * wondering why the numbers never move.
 */

private val SWEEP_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC)

private val CREATED_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneOffset.UTC)

fun DIV.reporterSection(data: SettingsPageData) {
    section(
        title = "Connected server",
        subtitle = "A Seam-enabled Minecraft server reads this world's tagged containers and reports what is in them.",
    ) {
        div("section__card") {
            id = "reporter-section"
            reporterSectionBody(data.world.id, data.reporterTokens, minted = null)
        }
    }
}

/**
 * The inner body of the section — the reveal (only on the response to a mint), the status, the
 * generate form and the live token list. Emits the single `.reporter-section` child of
 * `#reporter-section`, on full page load and as the HTMX `innerHTML` swap after a mint or revoke.
 */
fun FlowContent.reporterSectionBody(
    worldId: Int,
    tokens: List<ReporterTokenRow>?,
    minted: MintedReporterToken?,
) {
    div("reporter-section") {
        if (minted != null) mintedTokenReveal(worldId, minted)
        reporterStatus(tokens)
        generateReporterTokenForm(worldId)
        reporterTokenList(worldId, tokens)
    }
}

/**
 * The one and only time the raw token is rendered. It is not stored, so this block cannot be
 * reproduced by reloading the page — which the copy says plainly rather than leaving someone to
 * discover it.
 *
 * `hx-history="false"` is load-bearing, not decoration. Any HTMX request with `hx-push-url` — the
 * invitation tabs on this very page have it — makes htmx snapshot `document.body` into
 * `sessionStorage['htmx-history-cache']` BEFORE the swap. Without this attribute the plaintext
 * token is written to disk-backed storage readable by any same-origin script, and pressing Back
 * re-renders it. Both halves were reproduced in a browser against this page. htmx checks for
 * `[hx-history="false"]` anywhere in the document, so putting it here suppresses the snapshot
 * exactly while the token is on screen and stops suppressing once the reveal is swapped away.
 */
private fun FlowContent.mintedTokenReveal(worldId: Int, minted: MintedReporterToken) {
    div("callout reporter-reveal") {
        id = "reporter-token-reveal"
        attributes["hx-history"] = "false"
        attributes["role"] = "note"
        span("callout__icon") {
            attributes["aria-hidden"] = "true"
            +"!"
        }
        div("callout__body") {
            span("callout__lead") { +"Copy this token now." }
            +" It is shown once and is not stored — if you lose it, revoke it and generate another."
            code("reporter-reveal__token") { +minted.token }
            p("settings-form__helper subtle") {
                // The command, not the file. Seam Notebook 0.3.0 ships `/seam connect`, which
                // writes the config itself and starts reporting without a restart — telling an
                // admin to hand-write JSON and bounce their server instead is a worse instruction
                // for the same outcome.
                +"On the server, run "
                code("reporter-reveal__command") { +"/seam connect $worldId <token>" }
                +" from the console — a command typed in-game lands in the server log."
            }
        }
    }
}

/**
 * The loud no-reporter case, and the reassuring one — plus the third state that is neither.
 *
 * A null [tokens] means the lookup failed. Rendering that as "no server is connected" would be a
 * confident falsehood an admin would act on, so it gets its own honest, non-actionable message.
 */
private fun FlowContent.reporterStatus(tokens: List<ReporterTokenRow>?) {
    if (tokens == null) {
        div("callout callout--error reporter-status") {
            id = "reporter-status-unavailable"
            attributes["role"] = "note"
            span("callout__icon") {
                attributes["aria-hidden"] = "true"
                +"!"
            }
            div("callout__body") {
                span("callout__lead") { +"Couldn't load this world's server connection." }
                +(
                    " This says nothing about whether a server is connected — try reloading before " +
                        "generating a token."
                    )
            }
        }
        return
    }
    if (tokens.isEmpty()) {
        div("callout reporter-status") {
            id = "reporter-status-none"
            attributes["role"] = "note"
            span("callout__icon") {
                attributes["aria-hidden"] = "true"
                +"!"
            }
            div("callout__body") {
                span("callout__lead") { +"No server is connected to this world." }
                +(
                    " Containers can still be tagged, but nothing is reading them — every measured " +
                        "count will stay at zero until a server is connected."
                    )
            }
        }
        return
    }
    if (tokens.all { it.neverConnected }) {
        div("callout reporter-status") {
            id = "reporter-status-never"
            attributes["role"] = "note"
            span("callout__icon") {
                attributes["aria-hidden"] = "true"
                +"!"
            }
            div("callout__body") {
                span("callout__lead") { +"No server has used this token yet." }
                +(
                    " Run /seam connect on the server, then /seam status there to see what it " +
                        "makes of it."
                    )
            }
        }
    }
}

private fun FlowContent.generateReporterTokenForm(worldId: Int) {
    div("connect-server") {
        h3 { +"Generate a token" }
        form("connect-server__form") {
            encType = FormEncType.applicationXWwwFormUrlEncoded
            hxTarget("#reporter-section")
            attributes["hx-target-error"] = ".validation-error-message"
            hxSwap("innerHTML")
            hxPost("${Link.Worlds.world(worldId).settings().to}/reporter")
            attributes["hx-on::after-request"] = """
                if (event.detail.xhr.status >= 200 && event.detail.xhr.status < 300) {
                        this.reset();
                }
            """.trimIndent()
            div("connect-server__inputs") {
                div("input-group") {
                    label {
                        htmlFor = "reporter-token-name-input"
                        +"Server name"
                    }
                    input(type = InputType.text, name = "name", classes = "form-control") {
                        id = "reporter-token-name-input"
                        placeholder = "Mac Mini"
                        maxLength = "255"
                    }
                    p("settings-form__helper subtle") {
                        +"Optional. Only so several servers on one world stay tellable apart."
                    }
                }
            }
            p("validation-error-message") { id = "validation-error-name" }
            div("connect-server__actions") {
                button {
                    classes = setOf("btn", "btn--primary")
                    type = ButtonType.submit
                    +"Generate token"
                }
            }
        }
    }
}

private fun FlowContent.reporterTokenList(worldId: Int, tokens: List<ReporterTokenRow>?) {
    div("reporter-tokens") {
        h3 { +"Tokens" }
        ul("person-row-list") {
            id = "reporter-token-list"
            if (tokens == null) {
                li("person-row") {
                    id = "unavailable-reporter-token-list"
                    div("person-row__start") {
                        p("subtle") { +"Token list unavailable." }
                    }
                }
                return@ul
            }
            if (tokens.isEmpty()) {
                li("person-row") {
                    id = "empty-reporter-token-list"
                    div("person-row__start") {
                        p("subtle") { +"No reporter tokens for this world yet." }
                    }
                }
                return@ul
            }
            tokens.forEach { reporterTokenRow(worldId, it) }
        }
    }
}

private fun UL.reporterTokenRow(worldId: Int, token: ReporterTokenRow) {
    li("person-row") {
        id = "reporter-token-${token.id}"
        div("person-row__start") {
            div("person-row__info") {
                p("person-row__name") { +(token.name?.takeIf { it.isNotBlank() } ?: "Unnamed server") }
                // Who minted it and when. Without this, an owner auditing "what can currently write
                // to this world" cannot tell a colleague's token from one minted by an admin who has
                // since been demoted — leaving revoke-everything as the only safe move.
                p("subtle") {
                    +"Added ${CREATED_FMT.format(token.createdAt)}"
                    token.createdByName?.takeIf { it.isNotBlank() }?.let { +" by $it" }
                }
                div("row") {
                    if (token.neverConnected) {
                        badge("Never connected", BadgeVariant.NEUTRAL)
                    } else {
                        badge("Last sweep ${SWEEP_FMT.format(token.lastUsedAt)}", BadgeVariant.ACCENT)
                    }
                    token.reporterVersion?.takeIf { it.isNotBlank() }?.let {
                        badge(it, BadgeVariant.NEUTRAL)
                    }
                }
            }
        }
        div("person-row__end") {
            button {
                classes = setOf("btn", "btn--ghost", "btn--sm")
                type = ButtonType.button
                hxDeleteWithConfirm(
                    url = "${Link.Worlds.world(worldId).settings().to}/reporter/${token.id}",
                    title = "Revoke Token",
                    description = "Are you sure? A server using this token will stop reporting " +
                        "immediately, and the token cannot be recovered.",
                )
                hxTarget("#reporter-section")
                hxSwap("innerHTML")
                +"Revoke"
            }
        }
    }
}

/**
 * Top-level fragment renderer for the section body, used as the HTMX `innerHTML` response after a
 * mint or revoke so HTMX swaps `#reporter-section`'s contents with a fresh `.reporter-section`.
 */
fun renderReporterSectionBody(
    worldId: Int,
    tokens: List<ReporterTokenRow>?,
    minted: MintedReporterToken? = null,
): String = createHTML().div("reporter-section") {
    if (minted != null) mintedTokenReveal(worldId, minted)
    reporterStatus(tokens)
    generateReporterTokenForm(worldId)
    reporterTokenList(worldId, tokens)
}
