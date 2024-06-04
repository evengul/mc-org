package no.mcorg.presentation.htmx.templates.pages.resourcepack

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import no.mcorg.domain.ResourcePack
import no.mcorg.presentation.htmx.templates.hxDelete
import no.mcorg.presentation.htmx.templates.hxGet
import no.mcorg.presentation.htmx.templates.hxSwap
import no.mcorg.presentation.htmx.templates.hxTarget
import no.mcorg.presentation.htmx.templates.pages.page

fun resourcePacksPage(packs: List<ResourcePack>): String {
    return page("Resource Packs", "Resource Packs") {
        button {
            id = "packs-add-pack-show-form-button"
            type = ButtonType.button
            hxGet("/htmx/resourcepacks/add")
            hxTarget("#create-resource-pack-container")
            + "Create new resource pack"
        }
        div {
            id = "create-resource-pack-container"
        }
        ul {
            id = "pack-list"
            for (pack in packs) {
                li {
                    id = "pack-${pack.id}"
                    a {
                        id = "pack-${pack.id}-link"
                        href = "/resourcepacks/${pack.id}"
                        + pack.name
                    }
                    button {
                        id = "pack-${pack.id}-delete-button"
                        type = ButtonType.button
                        hxDelete("/resourcepacks/${pack.id}")
                        hxTarget("closest li")
                        hxSwap("outerHTML")
                        + "Delete resource pack"
                    }
                }
            }
        }
    }
}

fun addResourcePack(): String {
    return createHTML().form {
        id = "create-resource-pack-form"
        encType = FormEncType.multipartFormData
        method = FormMethod.post
        label {
            htmlFor = "create-resource-pack-name-input"
            + "Name"
        }
        input {
            id = "create-resource-pack-name-input"
            name = "resource-pack-name"
            required = true
            minLength = "3"
            maxLength = "120"
        }
        label {
            htmlFor = "create-resource-pack-version-input"
            + "Version (1.xx.x or 2xwxxa/b)"
        }
        input {
            id = "create-resource-pack-version-input"
            name = "resource-pack-version"
            required = true
            minLength = "1"
            maxLength = "10"
        }
        label {
            htmlFor = "create-resource-pack-type-input"
            + "Compatible server type"
        }
        select {
            id = "create-resource-pack-type-input"
            name = "resource-pack-type"
            option {
                value = "FABRIC"
                + "Fabric"
            }
            option {
                value = "VANILLA"
                + "Vanilla"
            }
            option {
                value = "FORGE"
                + "Forge"
            }
        }
        button {
            id = "create-resource-pack-submit"
            type = ButtonType.submit
            + "Create resource pack"
        }
    }
}
