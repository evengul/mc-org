package no.mcorg.presentation.htmx.handlers

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.mcorg.domain.Authority
import no.mcorg.domain.PermissionLevel
import no.mcorg.domain.ResourceType
import no.mcorg.domain.ServerType
import no.mcorg.presentation.configuration.packsApi
import no.mcorg.presentation.configuration.permissionsApi
import no.mcorg.presentation.htmx.routing.isHtml
import no.mcorg.presentation.htmx.templates.pages.resourcePacksPage

suspend fun ApplicationCall.handleResourcePacks(userId: Int) {
    val packs = permissionsApi().getPackPermissions(userId)
        .permissions[PermissionLevel.PACK]!!
        .map { it.first }

    isHtml()
    respond(resourcePacksPage(packs))
}

suspend fun ApplicationCall.handleCreateResourcePack() {
    val userId = request.cookies["MCORG-USER-ID"]?.toIntOrNull() ?: return respondRedirect("/signin")

    val parts = receiveMultipart().readAllParts()
    val name = (parts.find { it.name == "resource-pack-name" } as PartData.FormItem?)?.value
    val version = (parts.find { it.name == "resource-pack-version" } as PartData.FormItem?)?.value
    val type = (parts.find { it.name == "resource-pack-type" } as PartData.FormItem?)?.value?.toServerType()

    if (name == null || version == null || type == null) {
        respond(HttpStatusCode.BadRequest)
    } else {
        val id = packsApi().createPack(name, version, type)
        permissionsApi().addPackPermission(userId, id, Authority.OWNER)
        respondRedirect("/resourcepacks")
    }
}

suspend fun ApplicationCall.handleAddResourceToPack() {
    val packId = parameters["id"]?.toIntOrNull()

    val parts = receiveMultipart().readAllParts()
    val type = (parts.find { it.name == "resource-type" } as PartData.FormItem?)?.value?.toResourceType()
    val name = (parts.find { it.name == "resource-name" } as PartData.FormItem?)?.value

    if (packId == null || type == null || name == null) {
        respond(HttpStatusCode.BadRequest)
    } else {
        packsApi().addResource(packId, name, type, "")
        respondRedirect("/resourcepacks/$packId")
    }
}

private fun String.toServerType(): ServerType {
    when(this) {
        "FABRIC" -> return ServerType.FABRIC
        "FORGE" -> return ServerType.FORGE
        "VANILLA" -> return ServerType.VANILLA
    }

    throw IllegalArgumentException("Unknown server type: $this")
}

private fun String.toResourceType(): ResourceType {
    when(this) {
        "MOD" -> return ResourceType.MOD
        "MOD_PACK" -> return ResourceType.MOD_PACK
        "TEXTURE_PACK" -> return ResourceType.TEXTURE_PACK
        "DATA_PACK" -> return ResourceType.DATA_PACK
    }

    throw IllegalArgumentException("Unknown resource type: $this")
}