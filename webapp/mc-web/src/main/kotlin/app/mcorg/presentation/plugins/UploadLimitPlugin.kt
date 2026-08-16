package app.mcorg.presentation.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UploadLimit")

/**
 * Largest upload the schematic routes accept, before decompression.
 *
 * `.litematic` files are heavily compressed — the fixtures in `mc-nbt` are a few kilobytes and a
 * genuinely large community build lands well under a megabyte. 8 MB is generous enough that no
 * real file is refused while keeping the buffered request small against the 768 MB heap the
 * production JVM runs on. The parser applies its own, separate ceiling to the *decompressed*
 * bytes (`NbtLimits.MAX_DECOMPRESSED_BYTES`); this one bounds what reaches memory at all.
 */
const val MAX_SCHEMATIC_UPLOAD_BYTES: Long = 8L * 1024 * 1024

/**
 * Rejects an oversized upload on the declared `Content-Length`, before the body is read (MCO-345).
 *
 * Being a route-scoped plugin is the point. The schematic handlers buffer the whole part into a
 * `ByteArray` as their first act, and on the two world routes the `Role.ADMIN` check lives inside
 * the pipeline — so by the time authorization runs, an unauthorized member has already made the
 * server allocate the body. Running as a plugin puts the size check ahead of both.
 *
 * `Content-Length` is client-supplied and a chunked request omits it entirely, so this is the
 * cheap first line only; the parser's own budget is what holds when the header lies.
 */
val SchematicUploadLimitPlugin = createRouteScopedPlugin("SchematicUploadLimitPlugin") {
    onCall { call ->
        val declared = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: return@onCall
        if (declared > MAX_SCHEMATIC_UPLOAD_BYTES) {
            logger.info(
                "Rejected an upload to {} declaring {} bytes, over the {} byte limit",
                call.request.path(),
                declared,
                MAX_SCHEMATIC_UPLOAD_BYTES,
            )
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                "That file is too large. Schematics must be under ${MAX_SCHEMATIC_UPLOAD_BYTES / (1024 * 1024)} MB.",
            )
        }
    }
}
