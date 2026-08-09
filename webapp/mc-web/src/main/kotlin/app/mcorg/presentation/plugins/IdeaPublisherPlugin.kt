package app.mcorg.presentation.plugins

import app.mcorg.presentation.utils.getUser
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond

/**
 * Gates publishing an idea to the public hub (MCO-291).
 *
 * This used to gate *creating* an idea, which meant nobody could record a personal design without
 * a role only a database INSERT could grant — and the bank stayed empty. Creation is now open to
 * everyone; the curated step is putting a design in front of the community.
 *
 * The underlying role is still named `idea_creator` (`isIdeaCreator`): that string is a stored
 * value in `global_user_roles`, so renaming it needs a migration and is not worth one yet.
 */
val IdeaPublisherPlugin = createRouteScopedPlugin("IdeaPublisherPlugin") {
    onCall {
        if (!it.getUser().isIdeaCreator) {
            it.respond(HttpStatusCode.Forbidden, "You don't have permission to publish ideas to the hub.")
        }
    }
}
