package app.mcorg.presentation.templates.profile

import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.INPUT
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.stream.createHTML

fun createIsTechnicalCheckBox(isTechnical: Boolean) = createHTML().input {
    isTechnicalCheckBox(isTechnical)
}

fun INPUT.isTechnicalCheckBox(isTechnical: Boolean) {
    id = "profile-technical-player-input-check"
    checked = isTechnical
    hxPatch("/app/profile/is-technical-player")
    hxTarget("this")
    hxSwap("outerHTML")
    name = "technicalPlayer"
    type = InputType.checkBox
}