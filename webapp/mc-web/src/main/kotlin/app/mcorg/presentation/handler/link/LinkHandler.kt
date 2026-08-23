package app.mcorg.presentation.handler.link

import app.mcorg.api.ApproveDeviceCodeInput
import app.mcorg.api.ApproveDeviceCodeStep
import app.mcorg.api.GetDeviceCodeByUserCodeStep
import app.mcorg.config.AppConfig
import app.mcorg.pipeline.Result
import app.mcorg.presentation.templated.link.linkPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.host
import io.ktor.server.request.receiveParameters
import java.time.Instant

/** Normalises user input to the canonical `XXXX-XXXX` form (uppercase, dash-separated). */
private fun normalizeUserCode(raw: String): String {
    val cleaned = raw.uppercase().filter { it.isLetterOrDigit() }
    return if (cleaned.length == 8) "${cleaned.substring(0, 4)}-${cleaned.substring(4)}" else cleaned
}

/*
 * setLinkFramingHeaders() lived here, setting `frame-ancestors 'none'` and `X-Frame-Options: DENY`
 * on this route alone — the code-prefill form being the obvious clickjacking target. MCO-356 sends
 * both on every response from configureHTTP, so keeping it would only emit duplicate headers
 * (browsers intersect multiple CSPs, so it would not even be stricter). Removed rather than left
 * as a no-op.
 */

/**
 * CSRF gate for the state-changing POST (there is no CSRF-token infra). When an `Origin` (or, as a
 * fallback, `Referer`) header is present, its host must match the request host or the configured
 * APP_HOST; otherwise the request is treated as cross-origin. A malformed header is treated as
 * cross-origin (fail closed). When neither header is present, allow (don't break legit same-origin).
 */
private fun ApplicationCall.isCrossOriginPost(): Boolean {
    val header = request.header("Origin") ?: request.header("Referer") ?: return false
    val originHost = runCatching { Url(header).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return true
    val allowed = buildSet {
        add(request.host())
        AppConfig.appHost?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return originHost !in allowed
}

suspend fun ApplicationCall.handleGetLinkPage() {
    val user = getUser()
    val prefill = request.queryParameters["user_code"]?.let { normalizeUserCode(it) }
    respondHtml(linkPage(user = user, prefillCode = prefill))
}

suspend fun ApplicationCall.handleApproveLinkPage() {
    val user = getUser()
    if (isCrossOriginPost()) {
        respondHtml(
            linkPage(user = user, error = "This request could not be verified. Please open the link page directly and try again."),
            HttpStatusCode.Forbidden,
        )
        return
    }
    val params = receiveParameters()
    val rawCode = params["user_code"]?.trim().orEmpty()
    val userCode = normalizeUserCode(rawCode)

    if (userCode.isBlank()) {
        respondHtml(linkPage(user = user, prefillCode = rawCode, error = "Please enter a device code."))
        return
    }

    val row = when (val r = GetDeviceCodeByUserCodeStep.process(userCode)) {
        is Result.Success -> r.value
        is Result.Failure -> {
            respondHtml(linkPage(user = user, prefillCode = rawCode, error = "That code was not recognised. Check it and try again."))
            return
        }
    }

    if (row.status != "pending") {
        respondHtml(linkPage(user = user, prefillCode = rawCode, error = "That code has already been used or is no longer valid."))
        return
    }
    if (Instant.now().isAfter(row.expiresAt)) {
        respondHtml(linkPage(user = user, prefillCode = rawCode, error = "That code has expired. Request a new one in the mod."))
        return
    }

    when (val approved = ApproveDeviceCodeStep.process(ApproveDeviceCodeInput(userCode, user.id))) {
        is Result.Success -> if (approved.value > 0) {
            respondHtml(linkPage(user = user, success = "Device linked. You can return to the mod — it will finish connecting automatically."))
        } else {
            respondHtml(linkPage(user = user, prefillCode = rawCode, error = "That code has expired or was already used. Request a new one in the mod."))
        }
        is Result.Failure -> respondHtml(linkPage(user = user, prefillCode = rawCode, error = "Something went wrong linking the device. Please try again."))
    }
}
