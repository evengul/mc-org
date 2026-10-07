package app.mcorg.presentation.plugins

import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.routing.Route

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
 * Caps the whole request body of a schematic upload at [MAX_SCHEMATIC_UPLOAD_BYTES].
 *
 * Ktor's `RequestBodyLimit` covers both ways a body arrives. A declared `Content-Length` over the
 * cap is refused before the handler runs; a chunked body, which declares nothing, is counted as
 * the handler reads it and fails the read once it passes the cap. Our own plugin used to check
 * only the header, so a chunked request skipped the cap entirely and could stream an unbounded
 * number of multipart parts at the one shared vCPU (MCO-421).
 *
 * Route-scoped and installed on the route, ahead of the handler, because the two world routes
 * check `Role.ADMIN` inside their pipelines: an unauthorized member must be refused before the
 * server buffers the file, not after (MCO-345).
 *
 * Both cases end in a `PayloadTooLargeException`, which `configureStatusStaticRouter` answers with
 * a 413. Without that handler it would reach the catch-all and be reported as a 500.
 */
fun Route.limitSchematicUploads() {
    install(RequestBodyLimit) {
        bodyLimit { MAX_SCHEMATIC_UPLOAD_BYTES }
    }
}
