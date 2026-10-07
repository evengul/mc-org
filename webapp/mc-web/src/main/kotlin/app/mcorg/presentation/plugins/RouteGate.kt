package app.mcorg.presentation.plugins

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.RouteScopedPluginBuilder
import io.ktor.server.application.isHandled

/**
 * `onCall` for a plugin that gates a route: it stands down once an earlier plugin has answered.
 *
 * Ktor skips the route handler after a plugin responds, but every sibling route-scoped `onCall`
 * still runs. A gate that ran anyway did one of two things. It read an attribute the refusing
 * plugin never set and threw `No instance for key`: a signed-out `/admin` asked for the user that
 * AuthPlugin's redirect never stored, and the comment author check did the same (MCO-351). Or it
 * answered a second time and replaced the first answer: a signed-out visit to
 * `/worlds/not-a-world/roadmap` got WorldParamPlugin's 404 instead of the sign-in redirect
 * (MCO-158).
 *
 * Every route plugin in this package uses this instead of `onCall`, so the rule can't be forgotten
 * one plugin at a time. `RouteGateSourceScanTest` fails the build on a bare `onCall`, and names
 * the two authentication plugins it exempts.
 */
fun <C : Any> RouteScopedPluginBuilder<C>.onUnansweredCall(block: suspend (call: ApplicationCall) -> Unit) {
    onCall { call ->
        if (!call.isHandled) block(call)
    }
}
