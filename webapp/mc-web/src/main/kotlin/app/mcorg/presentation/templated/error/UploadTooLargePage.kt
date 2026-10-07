package app.mcorg.presentation.templated.error

import app.mcorg.presentation.plugins.MAX_SCHEMATIC_UPLOAD_BYTES

/** What a 413 says, on its own page or swapped into a form. Schematic uploads are the only capped bodies. */
const val UPLOAD_TOO_LARGE_MESSAGE =
    "That file is too large. Schematics must be under ${MAX_SCHEMATIC_UPLOAD_BYTES / (1024 * 1024)} MB."

/**
 * The schematic import modal is a plain multipart POST that navigates to its review page, so a
 * refused upload lands here as a whole page rather than as a fragment.
 */
fun uploadTooLargePage(): String = errorPageLayout(
    pageTitle = "413 — Upload Too Large · Seam",
    heading = "413 — Upload Too Large",
    body = UPLOAD_TOO_LARGE_MESSAGE,
    ctaText = "Back to worlds",
    ctaHref = "/worlds",
)
