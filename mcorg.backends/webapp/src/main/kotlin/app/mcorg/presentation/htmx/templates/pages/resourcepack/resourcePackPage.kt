package app.mcorg.presentation.htmx.templates.pages.resourcepack

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import app.mcorg.domain.ResourcePack
import app.mcorg.presentation.htmx.*
import app.mcorg.presentation.htmx.templates.pages.page

fun resourcePackPage(pack: ResourcePack): String {
    return page(title = pack.name, id = "resource-pack-page") {
        form {
            id = "resource-pack-add-resource-form"
            encType = FormEncType.multipartFormData
            method = FormMethod.post
            action = "/resourcepacks/${pack.id}"
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
        ul(classes = "resource-list") {
            id = "resource-list"
            for (resource in pack.resources.sortedBy { it.type }) {
                li(classes = "resource-list-item") {
                    id = "resource-pack-resource-${resource.id}"
                    + "[${resource.type}] ${resource.name}"
                    button(classes = "resource-list-item-delete-button") {
                        id = "resource-pack-resource-${resource.id}-delete-button"
                        type = ButtonType.button
                        hxDelete("/resourcepacks/${pack.id}/resources/${resource.id}")
                        hxTarget("closest li")
                        hxSwap("outerHTML")
                        hxConfirm("Are you sure you want to remove this resource from this resource pack?")
                        + "Delete"
                    }
                }
            }
        }
    }
}