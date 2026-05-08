package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.*
import app.mcorg.presentation.templated.layout.alert.ALERT_CONTAINER_ID
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

fun DIV.generalSection(data: SettingsPageData) {
    section("settings-section settings-section--general") {
        div("settings-section__heading") {
            h2 { +"General Settings" }
            p("settings-section__subtitle subtle") { +"Configure basic settings for your world" }
        }
        div("settings-section__body settings-section__card") {
            form { worldNameForm(data.world) }
            form { worldDescriptionForm(data.world) }
            form { worldVersionForm(data.world, data.supportedVersions) }
        }
    }
}
