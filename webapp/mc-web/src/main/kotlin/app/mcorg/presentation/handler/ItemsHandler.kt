package app.mcorg.presentation.handler

import app.mcorg.pipeline.items.handleSearchItems
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

class ItemsHandler {
    fun Route.itemRoutes() {
        route("/items") {
            get("/search") {
                call.handleSearchItems()
            }
        }
    }
}
