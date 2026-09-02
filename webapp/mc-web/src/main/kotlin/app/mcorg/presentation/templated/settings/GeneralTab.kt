package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.world.settings.general.WorldVersionImpact
import app.mcorg.presentation.*
import app.mcorg.presentation.templated.dsl.ALERT_CONTAINER_ID
import app.mcorg.presentation.templated.dsl.section
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import app.mcorg.engine.plan.MemberPrior

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

const val VERSION_IMPACT_ID = "world-version-impact"

/**
 * MCO-157: the Game Version selector, and the preflight that stands between it and the switch.
 *
 * Changing the select does *not* switch the version — it asks the server what switching would
 * cost, and renders the answer into [VERSION_IMPACT_ID]. Only the confirm button in that fragment
 * PATCHes. The two-step shape is the whole point: a world's stored item ids are the one thing a
 * version change can strand (see `WorldVersionImpact`), and the moment to see that list is before
 * the change, not after.
 */
fun FORM.worldVersionForm(world: World, supportedVersions: List<MinecraftVersion.Release>) {
    id = "world-version-form"
    classes += "settings-form"
    encType = FormEncType.applicationXWwwFormUrlEncoded

    label {
        htmlFor = "world-version-select"
        +"Game Version"
    }
    select(classes = "form-control") {
        name = "version"
        id = "world-version-select"
        hxGet("/worlds/${world.id}/settings/version/impact")
        hxTarget("#$VERSION_IMPACT_ID")
        hxSwap("innerHTML")
        hxTrigger("change")

        // Only versions this instance has ingested: anything else has an empty item catalog,
        // which the endpoint rejects for the same reason.
        supportedVersions.forEach { version ->
            option {
                value = version.toString()
                selected = version.toString() == world.version.toString()
                +version.toString()
            }
        }
    }
    p("settings-form__helper subtle") {
        +"Pick a version to see what changes before switching. Projects, plans and progress are kept."
    }
    div { id = VERSION_IMPACT_ID }
}

/**
 * The preflight answer, swapped in under the selector.
 *
 * A null [impact] means the selection is the version the world is already on — say nothing and
 * offer nothing, so re-picking the current version quietly clears a previous preview.
 */
fun versionImpactFragment(worldId: Int, impact: WorldVersionImpact?): String =
    createHTML().div("version-impact") {
        if (impact != null) versionImpactBody(worldId, impact)
    }

private fun FlowContent.versionImpactBody(worldId: Int, impact: WorldVersionImpact) {
    if (impact.isEmpty) {
        div("callout callout--success") {
            span("callout__icon") { +"✓" }
            div("callout__body") {
                +"Nothing in this world is lost by switching to ${impact.version}."
            }
        }
    } else {
        div("callout") {
            span("callout__icon") { +"!" }
            div("callout__body") {
                p {
                    +"Minecraft ${impact.version} does not have "
                    +countPhrase(impact.itemCount, "item")
                    +" this world still refers to, across "
                    +countPhrase(impact.projectCount, "project")
                    +". They are kept, not deleted — the plan will show them as missing so you can swap them."
                }
                impact.projects.forEach { project ->
                    div("version-impact__project") {
                        span("section-label") { +project.projectName }
                        ul("version-impact__items") {
                            project.items.forEach { item ->
                                li {
                                    span("version-impact__item-name") { +item.name }
                                    span("subtle") {
                                        +" — ${item.usages.joinToString(", ") { it.label }}"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    div("version-impact__actions") {
        button(classes = "btn btn--primary btn--sm") {
            type = ButtonType.button
            hxPatch("/worlds/$worldId/settings/version")
            // The select is the single source for which version is being switched to, so the
            // button cannot drift from the preview the user just read.
            hxInclude("#world-version-select")
            hxTarget("#$ALERT_CONTAINER_ID")
            hxSwap("afterbegin")
            +"Switch to ${impact.version}"
        }
    }
}

private fun countPhrase(count: Int, noun: String): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"

/**
 * The same gap list, shown unprompted when the world's *current* version already strands ids —
 * a world can arrive here by a switch made earlier, and the notice should not depend on having
 * just used the selector.
 */
fun FlowContent.currentVersionGapNotice(impact: WorldVersionImpact) {
    if (impact.isEmpty) return
    div("callout") {
        id = "world-version-gap-notice"
        span("callout__icon") { +"!" }
        div("callout__body") {
            +"This world refers to "
            +countPhrase(impact.itemCount, "item")
            +" that Minecraft ${impact.version} does not have. They show as missing in the affected plans."
        }
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

/**
 * Which tree this world farms (MCO-409).
 *
 * Beside the farm-scale threshold because it is the same kind of fact — one answer about the
 * world's infrastructure that every project's plan reads. Without it, a large import asks
 * "which planks?", "which wooden slab?" and "which log?" separately, and nobody holds three
 * independent opinions about that.
 *
 * A select rather than free text: the vocabulary is the engine's ([MemberPrior.SPECIES]), so
 * the options here cannot drift from what the planner will match against.
 */
fun FORM.preferredWoodSpeciesForm(world: World) {
    id = "world-wood-species-form"
    classes += "settings-form"
    hxTargetError(".validation-error-message")
    encType = FormEncType.applicationXWwwFormUrlEncoded

    hxTarget("#$ALERT_CONTAINER_ID")
    hxSwap("afterbegin")
    hxPatch("/worlds/${world.id}/settings/preferred-wood-species")
    hxTrigger("change from:#world-wood-species-select")

    label {
        htmlFor = "world-wood-species-select"
        +"Wood you farm"
    }
    select(classes = "form-control") {
        name = "preferredWoodSpecies"
        id = "world-wood-species-select"

        // Empty is a real answer, and the default one: it means "keep asking me".
        option {
            value = ""
            selected = world.preferredWoodSpecies == null
            +"Ask me each time"
        }
        MemberPrior.SPECIES.forEach { species ->
            option {
                value = species
                selected = species == world.preferredWoodSpecies
                +species.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            }
        }
    }
    p("settings-form__helper subtle") {
        +"Used wherever a recipe accepts any wood, so the plan stops asking once per tag. "
        +"It never changes what a project asked for \u2014 a build that needs oak planks still needs oak planks. "
        +"Bamboo answers plank and slab recipes but not ones needing a log, so those keep asking."
    }
    p("validation-error-message") {
        id = "validation-error-preferred-wood-species"
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
        data.currentVersionImpact?.let { currentVersionGapNotice(it) }
        form { worldVersionForm(data.world, data.supportedVersions) }
        form { farmScaleThresholdForm(data.world) }
        form { preferredWoodSpeciesForm(data.world) }
    }
}
