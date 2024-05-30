package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import no.mcorg.domain.ResourcePack
import no.mcorg.presentation.htmx.templates.hxDelete
import no.mcorg.presentation.htmx.templates.hxGet
import no.mcorg.presentation.htmx.templates.hxSwap
import no.mcorg.presentation.htmx.templates.hxTarget

fun resourcePackPage(pack: ResourcePack): String {
    return page(title = pack.name) {
        p {
            + "This is a resource pack you have made"
        }
        button {
            type = ButtonType.button
            hxGet("/htmx/add-resource-to-pack")
            hxTarget("#add-resource-container")
            hxSwap("outerHTML")
            + "Add resource to resource pack"
        }
        div {
            id = "add-resource-container"
        }
        ul {
            for (resource in pack.resources.sortedBy { it.type }) {
                li {
                    + "[${resource.type}] ${resource.name}"
                    button {
                        type = ButtonType.button
                        hxDelete("/resourcepacks/${pack.id}/resource/${resource.id}")
                        hxTarget("closest li")
                        hxSwap("outerHTML")
                        + "Delete"
                    }
                }
            }
        }
    }
}

fun addResourceToPack(): String {
    return createHTML().form {
        encType = FormEncType.multipartFormData
        method = FormMethod.post
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
            type = ButtonType.submit
            + "Add resource to pack"
        }
    }
}