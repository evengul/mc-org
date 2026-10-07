package app.mcorg.presentation.templated.error

import app.mcorg.presentation.plugins.UPLOAD_TOO_LARGE_MESSAGE

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
