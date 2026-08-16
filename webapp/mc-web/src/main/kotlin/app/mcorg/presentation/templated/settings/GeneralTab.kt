package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.*
import app.mcorg.presentation.templated.dsl.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.dsl.section
import kotlinx.html.*

fun FORM.worldNameForm(world: World) {
    id = "world-name-form"
    classes += "settings-form"
    hxTargetError(".validation-error-message")
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#$ALERT_CONTAINER_ID")
    hxSwap("afterbegin")
    hxPatch("/worlds/${world.id}/settings/name")
    hxTrigger("input changed delay:500ms from:#world-name-input, submit")

    label {
        htmlFor = "world-name-input"
        +"World Name"
        span("required-indicator") { +"*" }
    }
    input(classes = "form-control") {
        name = "name"
        id = "world-name-input"
        type = InputType.text
        value = world.name
        required = true
        minLength = "3"
        maxLength = "100"
    }
    p("validation-error-message") {
        id = "validation-error-name"
    }
}

fun FORM.worldDescriptionForm(world: World) {
    id = "world-description-form"
    classes += "settings-form"
    hxTargetError(".validation-error-message")
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#$ALERT_CONTAINER_ID")
    hxSwap("afterbegin")
    hxPatch("/worlds/${world.id}/settings/description")
    hxTrigger("input changed delay:500ms from:#world-description-input, submit")

    label {
        htmlFor = "world-description-input"
        +"World Description"
    }
    textArea(classes = "form-control") {
        name = "description"
        id = "world-description-input"
        maxLength = "500"
        +world.description
    }
    p("validation-error-message") {
        id = "validation-error-description"
    }
}

@Suppress("UNUSED_PARAMETER")
fun FORM.worldVersionForm(world: World, supportedVersions: List<MinecraftVersion.Release>) {
    id = "world-version-form"
    classes += "settings-form settings-form--disabled"
    encType = FormEncType.applicationXWwwFormUrlEncoded

    label {
        htmlFor = "world-version-select"
        +"Game Version"
    }
    select(classes = "form-control") {
        name = "version"
        id = "world-version-select"
        disabled = true
        option {
            value = world.version.toString()
            selected = true
            +"${world.version}"
        }
    }
    p("settings-form__helper subtle") {
        +"Upgrade possibility coming soon — switching versions safely requires migrating projects, recipes, and resource graphs."
    }
}

/**
 * The farm-scale threshold (MCO-401) — raw demand at or above this is marked "worth a farm"
 * in every project's plan.
 *
 * Lives in General rather than a planning tab of its own because it is a fact about the world's
 * scale, alongside its name and version: a superflat testing world and a megabase want different
 * numbers, and the same number applies to every project in the world.
 */
fun FORM.farmScaleThresholdForm(world: World) {
    id = "world-farm-scale-form"
    classes += "settings-form"
    hxTargetError(".validation-error-message")
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#$ALERT_CONTAINER_ID")
    hxSwap("afterbegin")
    hxPatch("/worlds/${world.id}/settings/farm-scale-threshold")
    hxTrigger("input changed delay:500ms from:#world-farm-scale-input, submit")

    label {
        htmlFor = "world-farm-scale-input"
        +"Worth a farm above"
    }
    input(classes = "form-control") {
        name = "farmScaleThreshold"
        id = "world-farm-scale-input"
        type = InputType.number
        value = world.farmScaleThreshold.toString()
        required = true
        min = "1"
        max = "10000000"
    }
    p("settings-form__helper subtle") {
        +"Raw materials a project needs this many of are marked as worth building a farm for. "
        +"The default, 1,728, is one shulker box."
    }
    p("validation-error-message") {
        id = "validation-error-farm-scale-threshold"
    }
}

fun DIV.generalSection(data: SettingsPageData) {
    section(
        title = "General Settings",
        subtitle = "Configure basic settings for your world",
        card = true,
    ) {
        form { worldNameForm(data.world) }
        form { worldDescriptionForm(data.world) }
        form { worldVersionForm(data.world, data.supportedVersions) }
        form { farmScaleThresholdForm(data.world) }
    }
}
