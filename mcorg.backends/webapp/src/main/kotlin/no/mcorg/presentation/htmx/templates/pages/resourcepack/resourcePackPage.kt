package no.mcorg.presentation.htmx.templates.pages.resourcepack

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import no.mcorg.domain.ResourcePack
import no.mcorg.presentation.htmx.templates.hxDelete
import no.mcorg.presentation.htmx.templates.hxGet
import no.mcorg.presentation.htmx.templates.hxSwap
import no.mcorg.presentation.htmx.templates.hxTarget
import no.mcorg.presentation.htmx.templates.pages.page

fun resourcePackPage(pack: ResourcePack): String {
    return page(title = pack.name) {
        button {
            id = "resource-pack-add-resource-show-form-button"
            type = ButtonType.button
            hxGet("/htmx/resourcepacks/${pack.id}/resources/add")
            hxTarget("#add-resource-container")
            hxSwap("outerHTML")
            + "Add resource to resource pack"
        }
        div {
            id = "add-resource-container"
        }
        ul {
            id = "resource-list"
            for (resource in pack.resources.sortedBy { it.type }) {
                li {
                    id = "resource-pack-resource-${resource.id}"
                    + "[${resource.type}] ${resource.name}"
                    button {
                        id = "resource-pack-resource-${resource.id}-delete-button"
                        type = ButtonType.button
                        hxDelete("/resourcepacks/${pack.id}/resources/${resource.id}")
                        hxTarget("closest li")
                        hxSwap("outerHTML")
                        + "Delete"
                    }
                }
            }
        }
    }
}

fun addResourceToPack(resourcePackId: Int): String {
    return createHTML().form {
        id = "resource-pack-add-resource-form"
        encType = FormEncType.multipartFormData
        method = FormMethod.post
        action = "/resourcepacks/$resourcePackId"
        label {
            htmlFor = "add-resource-type-input"
            + "Resource type"
        }
        select {
            id = "add-resource-type-input"
            name = "resource-type"
            option {
                value = "TEXTURE_PACK"
                + "Texture Pack"
            }
            option {
                value = "DATA_PACK"
                + "Data Pack"
            }
            option {
                value = "MOD_PACK"
                + "Mod Pack"
            }
            option {
                value = "MOD"
                + "Singular mod"
            }
        }
        label {
            htmlFor = "add-resource-name-input"
            + "Name of resource"
        }
        input {
            id = "add-resource-name-input"
            name = "resource-name"
            required = true
            minLength = "3"
            maxLength = "120"
        }
        label {
            htmlFor = "add-resource-url-input"
            + "Download url (.zip or .jar)"
        }
        input {
            type = InputType.url
            name = "resource-url"
            required = true
            maxLength = "2000"
        }
        button {
            id = "resource-pack-add-resource-submit"
            type = ButtonType.submit
            + "Add resource to pack"
        }
    }
}