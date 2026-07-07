package app.mcorg.presentation.handler.link

import app.mcorg.api.ApproveDeviceCodeInput
import app.mcorg.api.ApproveDeviceCodeStep
import app.mcorg.api.GetDeviceCodeByUserCodeStep
import app.mcorg.pipeline.Result
import app.mcorg.presentation.templated.link.linkPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import java.time.Instant

/** Normalises user input to the canonical `XXXX-XXXX` form (uppercase, dash-separated). */
private fun normalizeUserCode(raw: String): String {
    val cleaned = raw.uppercase().filter { it.isLetterOrDigit() }
    return if (cleaned.length == 8) "${cleaned.substring(0, 4)}-${cleaned.substring(4)}" else cleaned
}

suspend fun ApplicationCall.handleGetLinkPage() {
    val user = getUser()
    val prefill = request.queryParameters["user_code"]?.let { normalizeUserCode(it) }
    respondHtml(linkPage(user = user, prefillCode = prefill))
}

suspend fun ApplicationCall.handleApproveLinkPage() {
    val user = getUser()
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
