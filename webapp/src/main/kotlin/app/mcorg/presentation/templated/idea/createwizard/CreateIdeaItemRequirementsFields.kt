package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.presentation.*
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.form.searchableselect.SearchableSelectOption
import app.mcorg.presentation.templated.common.form.searchableselect.searchableSelect
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import kotlinx.coroutines.runBlocking
import kotlinx.html.*

fun FORM.itemRequirementFields(data: CreateIdeaWizardData) {
    p {
        +"Specify any item requirements for your idea. Only items available in the selected version range will be shown."
    }
    label {
        htmlFor = "item-requirements-litematica-file"
        + "Litematica File:"
    }
    input {
        id = "item-requirements-litematica-file"
        name = "litematicaFile"
        type = InputType.file
        classes += "form-control"
    }

    div {
        searchableSelect(
            id = "item-requirements-items-select",
            name = "itemId",
            options = runBlocking { (GetItemsInVersionRangeStep.process(data.versionRange ?: MinecraftVersionRange.Unbounded).getOrNull() ?: emptyList()) }.map {
                SearchableSelectOption(
                    value = it.id,
                    label = it.name
                )
            }
        )
        span("required-indicator") { +"*" }
        p("validation-error-message") {
            id = "validation-error-itemId"
        }
        input {
            type = InputType.number
            id = "item-requirements-amount"
            name = "itemAmount"
            min = "1"
            max = "2000000000"
            value = "1"
            classes += "form-control"
        }
        span("required-indicator") { +"*" }
        p("validation-error-message") {
            id = "validation-error-itemAmount"
        }
        neutralButton("Add Item") {
            id = "item-requirements-add-item-button"
            buttonBlock = {
                type = ButtonType.button

                hxGet(Link.Ideas.to + "/create/item-requirement-field")
                hxTarget("#idea-item-requirements-list")
                hxTargetError(".validation-error-message")
                hxSwap("afterbegin")
                hxInclude("[name='itemId'], [name='itemAmount'], [name='versionRangeType'], [name='versionFrom'], [name='versionTo']")
            }
        }
    }

    ul {
        id = "idea-item-requirements-list"
        data.itemRequirements?.let {
            it.toSortedMap { a, b -> a.name.compareTo(b.name) }.forEach { (item, quantity) ->
                li {
                    itemRequirementListEntry(item, quantity)
                }
            }
        }
    }
}

fun LI.itemRequirementListEntry(item: Item, quantity: Int) {
    id = "idea-item-requirement-${item.id}"
    +"${item.name} x $quantity"
    iconButton(Icons.DELETE, "Remove ${item.name} requirement") {
        buttonBlock = {
            type = ButtonType.button

            //language=js
            onClick = """
                const element = document.getElementById('idea-item-requirement-${item.id}');
                const hiddenElement = document.getElementById('hidden-item-requirement-${item.id}');
                if (element) {
                    element.remove();
                } else console.error("Could not find element with id idea-item-requirement-${item.id} to remove");
                if (hiddenElement) {
                    hiddenElement.remove();
                } else console.error("Could not find hidden element with id hidden-item-requirement-${item.id} to remove");
            """.trimIndent()
        }
    }
}