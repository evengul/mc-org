package app.mcorg.presentation.plugins

import app.mcorg.pipeline.resources.IsStorageTrackedStep
import app.mcorg.presentation.handler.respondRefusal
import app.mcorg.presentation.utils.getProjectId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin

/**
 * Refuses a typed count on a storage-tracked project (MCO-540).
 *
 * Installed on the routes that write `collected` by hand. A tracked project's counts follow its
 * chests, and the next sweep would overwrite whatever was typed within 30 seconds — people type
 * into a number that is about to be replaced and conclude the product is broken. The page draws no
 * counter for a tracked project; this is the half that holds for a page opened before the switch
 * was flipped, and for anything that sends the request without a page.
 *
 * A route plugin rather than a check in each pipeline, so the rule lives in the route tree beside
 * the routes it covers, where a new counter route is added.
 */
val CountsFollowChestsPlugin = createRouteScopedPlugin("CountsFollowChestsPlugin") {
    onUnansweredCall {
        // A failed lookup reads as untracked: the write right after it goes to the same database,
        // and a count that does slip through is put back by the next sweep.
        val tracked = IsStorageTrackedStep.process(it.getProjectId()).getOrNull() ?: false
        if (tracked) {
            it.respondRefusal(
                HttpStatusCode.Conflict,
                "Counted from chests",
                "This project's counts follow its tagged chests. To type a count, turn off " +
                    "\"Count from chests\" in the project's settings.",
                alertId = "counts-follow-chests",
            )
        }
    }
}
