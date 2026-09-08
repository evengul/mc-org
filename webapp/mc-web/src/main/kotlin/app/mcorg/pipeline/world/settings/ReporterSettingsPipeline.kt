package app.mcorg.pipeline.world.settings

import app.mcorg.api.ApiCrypto
import app.mcorg.api.CreateReporterTokenInput
import app.mcorg.api.CreateReporterTokenStep
import app.mcorg.api.ListReporterTokensStep
import app.mcorg.api.MintedReporterToken
import app.mcorg.api.ReporterTokenKey
import app.mcorg.api.RevokeReporterTokenStep
import app.mcorg.pipeline.Result
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.settings.renderReporterSectionBody
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond

/**
 * World settings → connected server (MCO-531): minting and revoking reporter tokens.
 *
 * The reporter authenticates as itself, not as a player, because its token lives in a plaintext
 * config file on a machine whose operator is not necessarily the world owner. Minting is gated by
 * `WorldAdminPlugin` like every other route under `/settings` — the plugin does the authorization,
 * never this pipeline.
 */

/** Cap the operator's label at the column width; it is a display name, not data. */
private const val MAX_TOKEN_NAME_LENGTH = 255

/**
 * Truncate to at most [max] UTF-16 units without splitting a surrogate pair. `VARCHAR(n)` counts
 * characters and `String.length` counts UTF-16 units, so this is always at least as strict as the
 * column — the point is only to never emit a lone surrogate.
 */
private fun String.truncateAtCodePoint(max: Int): String {
    if (length <= max) return this
    val end = if (Character.isHighSurrogate(this[max - 1])) max - 1 else max
    return substring(0, end)
}

private suspend fun ApplicationCall.respondReporterSection(
    worldId: Int,
    minted: MintedReporterToken? = null,
) {
    // Null (not empty) on failure, so the section can say "couldn't load" instead of asserting
    // that no server is connected.
    val tokens = (ListReporterTokensStep.process(worldId) as? Result.Success)?.value
    respondHtml(renderReporterSectionBody(worldId, tokens, minted))
}

/**
 * Mints a token and renders it back **once**. The raw value is never persisted — only its SHA-256
 * hash — so this response is the only place it ever exists outside the server's config file. It is
 * therefore deliberately not logged, and [MintedReporterToken] redacts it in `toString()`.
 */
suspend fun ApplicationCall.handleMintReporterToken() {
    val worldId = this.getWorldId()
    val userId = this.getUser().id
    val parameters = this.receiveParameters()
    // Truncate on a code-point boundary. A bare `take` cuts UTF-16 units, so a name whose 255th
    // unit is a high surrogate would be stored as a lone surrogate — which pgjdbc cannot encode as
    // UTF-8, turning a cosmetic overflow into a 500 on the mint.
    val name = parameters["name"]?.trim()?.takeIf { it.isNotBlank() }?.truncateAtCodePoint(MAX_TOKEN_NAME_LENGTH)

    val token = ApiCrypto.newToken()

    // The only response in the app carrying a plaintext credential. A POST is already
    // non-cacheable per RFC 9111 absent explicit freshness, so this asserts the property rather
    // than inheriting it. (It does nothing for htmx's sessionStorage history cache — that is
    // handled by `hx-history="false"` on the reveal itself.)
    response.headers.append(HttpHeaders.CacheControl, "no-store")

    handlePipeline(
        onSuccess = { row -> respondReporterSection(worldId, MintedReporterToken(row, token)) },
    ) {
        CreateReporterTokenStep.run(
            CreateReporterTokenInput(
                worldId = worldId,
                tokenHash = ApiCrypto.sha256Hex(token),
                name = name,
                createdBy = userId,
            )
        )
    }
}

suspend fun ApplicationCall.handleRevokeReporterToken() {
    val worldId = this.getWorldId()
    val tokenId = parameters["tokenId"]?.toLongOrNull()
    if (tokenId == null) {
        respond(HttpStatusCode.BadRequest, "Invalid token id")
        return
    }
    handlePipeline(
        onSuccess = { respondReporterSection(worldId) },
    ) {
        // Scoped by world in SQL, so an admin of another world cannot revoke this one's token.
        RevokeReporterTokenStep.run(ReporterTokenKey(worldId = worldId, id = tokenId))
    }
}
